/*
 * Copyright 2006-2022 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package io.github.mzmine.modules.dataprocessing.filter_typeiicorrection;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.FeatureDataUtils;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.DetectionType;
import io.github.mzmine.datamodel.features.types.FeatureDataType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureListRowSorter;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeIICorrectionTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(TypeIICorrectionTask.class.getName());

  private final ParameterSet parameters;
  private final MZmineProject project;
  private final ModularFeatureList originalFeatureList;
  private final MZTolerance featureToPatternMzTol = new MZTolerance(0.005, 5);
  private final OriginalFeatureListOption handleOriginal;
  private final int areaCutoff;
  private final double mergeWidth;

  // features counter
  private int finished;
  private int totalRows;

  //Counter for logging.
  private int rtMismatch;
  private int changedFeatures;
  private int removedFeatures;

  /**
   * Constructor used to extract all parameters
   *
   * @param project        the current MZmineProject
   * @param featureList    runs this taks on this featureList
   * @param parameters     user parameters
   * @param storage        memory mapping is only used for memory intensive data that should be
   *                       stored for later processing - like spectra, feature data, ... so storage
   *                       is likely null here to process all in memory
   * @param moduleCallDate used internally to track the order of applied methods
   */
  public TypeIICorrectionTask(MZmineProject project, FeatureList featureList,
      ParameterSet parameters, @Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.project = project;
    this.originalFeatureList = (ModularFeatureList) featureList;
    this.parameters = parameters;

    // Get parameter values for easier use
    handleOriginal = parameters.getValue(TypeIICorrectionParameters.handleOriginal);
    mergeWidth = parameters.getValue(TypeIICorrectionParameters.mergeWidth);
    areaCutoff = parameters.getValue(TypeIICorrectionParameters.areaCutoff);
  }

  @Override
  public String getTaskDescription() {
    return "Runs task on " + originalFeatureList;
  }

  @Override
  public void run() {

    //Setting the status of the task.
    setStatus(TaskStatus.PROCESSING);
    logger.info("Running task on " + originalFeatureList);

    // check for cancelled state and stop
    if (isCanceled()) {
      return;
    }

    //Setting number of rows to calculate finished percentage.
    totalRows = originalFeatureList.getNumberOfRows();

    //Correcting the type-II-overlap in the original feature list and coping it to a new feature list.
    ModularFeatureList processedFeatureList = correctTypIIOverlapInFeatureList(originalFeatureList);

    //Reflecting the new feature list to the project.
    handleOriginal.reflectNewFeatureListToProject("typeII", project, processedFeatureList,
        originalFeatureList);

    //Logging statistics.
    logger.info(
        "Times retention times mismatched: " + rtMismatch + "\n Number of changed features: "
            + changedFeatures + "\n Number of removed features: " + removedFeatures);

    // add to project
    addAppliedMethodsAndResultToProject();

    logger.info("Finished on " + originalFeatureList);
    setStatus(TaskStatus.FINISHED);
  }


  private ModularFeatureList correctTypIIOverlapInFeatureList(ModularFeatureList featureList) {

    //Creating a copy of the used Feature List.
    ModularFeatureList processedFeatureList = featureList.createCopy(
        featureList.getName() + " type2corr", getMemoryMapStorage(), false);

    //Extracting raw data files used in the feature List.
    final List<RawDataFile> rawDataFiles = processedFeatureList.getRawDataFiles();

    //Loop going over all the raw data files used in the feature list.
    for (final RawDataFile file : rawDataFiles) {

      correctTypeIIOverlapInFile(file, processedFeatureList);
    }

    return processedFeatureList;
  }

  private void correctTypeIIOverlapInFile(RawDataFile file,
      ModularFeatureList processedFeatureList) {

    //Getting rows from the feature List.
    List<FeatureListRow> rows = processedFeatureList.getRows();
    rows.sort(FeatureListRowSorter.MZ_ASCENDING);

    //Loop going over all the rows of a given raw data file of the feature list.
    for (int i = 0; i < rows.size() - 1; i++) {

      //Getting a row from the list of all rows.
      final FeatureListRow row = rows.get(i);

      //Getting the feature from the row for the given raw data file.
      //Continuing with the next row, if there is no feature for this row in the given raw data file.
      ModularFeature monoisotopicFeature = (ModularFeature) row.getFeature(file);
      if (monoisotopicFeature == null) {
        continue;
      }

      //Calculating the predicted isotope pattern for the features charged molecular formula.
      IsotopePattern isotopePattern = TypeIICorrectionUtils.getIsotopePatternForFeature(
          monoisotopicFeature, mergeWidth);
      if (isotopePattern == null) {
        continue;
      }

      //Calculating the isotope patterns mz range considering the mz tolerance.
      Range<Double> totalIsotopePatternMzRange = featureToPatternMzTol.getToleranceRange(
          isotopePattern.getDataPointMZRange());

      // Loop over the following features.
      for (int j = i + 1; j < rows.size() - 1; j++) {

        //Getting the feature from the following row.
        //Checking weather there is a feature in the following row for the given raw data file.
        FeatureListRow followingRow = rows.get(j);
        ModularFeature overlapFeature = (ModularFeature) followingRow.getFeature(file);
        if (overlapFeature == null) {
          continue;
        }

        //Breaking the loop if the following feature is outside the isotope pattern mz range.
        //Continuing with the next monoisotopic feature.
        if (!totalIsotopePatternMzRange.contains(overlapFeature.getMZ())) {
          break;
        }

        //Checking weather the retention times of the given feature and the following feature overlap.
        //Continuing with the next row if they don't overlap.
        if (!monoisotopicFeature.getRawDataPointsRTRange()
            .isConnected(overlapFeature.getRawDataPointsRTRange())) {
          continue;
        }

        //Checking weather the overlap feature mz overlaps with the isotope pattern in a given mz range.
        int isotopePatternIndex = TypeIICorrectionUtils.getMatchingIndexInIsotopePattern(
            isotopePattern, overlapFeature.getMZ(), featureToPatternMzTol);
        if (isotopePatternIndex == -1) {
          continue;
        }

        // Getting feature intensity data both overlapping features.
        IonTimeSeries<? extends Scan> monoisotopicEic = monoisotopicFeature.getFeatureData();
        IonTimeSeries<? extends Scan> overlapEic = overlapFeature.getFeatureData();

        //Getting the overlapping rt region.
        Range<Float> featuresRtIntersection = monoisotopicFeature.getRawDataPointsRTRange()
            .intersection(overlapFeature.getRawDataPointsRTRange());
        float lowerRtOverlap = featuresRtIntersection.lowerEndpoint();
        float upperRtOverlap = featuresRtIntersection.upperEndpoint();

        //The indices for the overlap region of the monoisotopic feature.
        int monoisotopicFeatureLowerIndex = TypeIICorrectionUtils.getIndexForRt(monoisotopicEic,
            lowerRtOverlap);
        int monoisotopicFeatureUpperIndex = TypeIICorrectionUtils.getIndexForRt(monoisotopicEic,
            upperRtOverlap);

        //The indices for the overlap region of the overlap feature.
        int overlapFeatureLowerIndex = TypeIICorrectionUtils.getIndexForRt(overlapEic,
            lowerRtOverlap);
        int overlapFeatureUpperIndex = TypeIICorrectionUtils.getIndexForRt(overlapEic,
            upperRtOverlap);

        //Checking weather there are jumps in the retention times between the two features.
        //Continuing with the next overlap feature if there are.
        if (!TypeIICorrectionUtils.rtsMatching(monoisotopicEic, monoisotopicFeatureLowerIndex,
            monoisotopicFeatureUpperIndex, overlapEic, overlapFeatureLowerIndex)) {

          //Increasing the counter for logging the number of times the rts didn't match.
          rtMismatch++;
          continue;
        }

        //Calculating the relative intensity of the monoisotopic features isotope.
        IsotopePattern relativeIsotopePattern = isotopePattern.getRelativeIntensityCopy();
        final double relativeIsotopeIntensity = relativeIsotopePattern.getIntensityValue(
            isotopePatternIndex);

        double[] correctedIntensities = TypeIICorrectionUtils.subtractIsotopeIntensities(
            monoisotopicEic, monoisotopicFeatureLowerIndex, overlapEic, overlapFeatureLowerIndex,
            overlapFeatureUpperIndex, relativeIsotopeIntensity);

        addCorrectedCorrectedIntensitiesToFeature(overlapFeature, correctedIntensities,
            monoisotopicFeature);
        removeFeatureBelowCutoff(overlapFeature, areaCutoff, processedFeatureList);
      }

      // Update progress
      finished++;
    }
  }

  private void addCorrectedCorrectedIntensitiesToFeature(ModularFeature overlapFeature,
      double[] correctedIntensities, ModularFeature monoisotopicFeature) {

    //Extracting the overlap feature from the overlap eic.
    IonTimeSeries<? extends Scan> overlapEic = overlapFeature.getFeatureData();

    //Getting overlap feature mz values.
    double[] overlapFeatureMzs = overlapEic.getMzValues(new double[overlapEic.getNumberOfValues()]);

    //Area of the overlap feature before correction for logging purposes.
    double overlapFeatureArea = overlapFeature.getArea();

    //Writing the new intensities to the feature.
    IonTimeSeries<? extends Scan> correctedOverlapEic = (IonTimeSeries<? extends Scan>) overlapEic.copyAndReplace(
        getMemoryMapStorage(), overlapFeatureMzs, correctedIntensities);
    overlapFeature.set(FeatureDataType.class, correctedOverlapEic);

    //Changing the detection type to manual so that changed features can be identified in the feature list.
    overlapFeature.set(DetectionType.class, FeatureStatus.MANUAL);

    //Recalculating the overlap features data which depends on the changed intensity data.
    FeatureDataUtils.recalculateIonSeriesDependingTypes(overlapFeature);

    //Area of the overlap feature after correction for logging purposes.
    double correctedOverlapFeatureArea = overlapFeature.getArea();

    //Increasing the counter for logging the total number of changed features.
    changedFeatures++;

    //Logging information of the changed features.
    logger.info(
        "Overlap feature ID: " + overlapFeature.getRow().getID() + "\n Monoisotopic Feature ID: "
            + monoisotopicFeature.getRow().getID() + "\n Area changed from " + overlapFeatureArea
            + " to " + correctedOverlapFeatureArea);
  }


  private void removeFeatureBelowCutoff(ModularFeature overlapFeature, int areaCutoff,
      ModularFeatureList processedFeatureList) {

    FeatureListRow row = overlapFeature.getRow();

    //Removing a feature if its corrected area is below 100.
    if (overlapFeature.getArea() < areaCutoff) {

      row.removeFeature(overlapFeature.getRawDataFile());
      removedFeatures++;
    }

    //Removing a row if it doesn't contain any features anymore.
    if (row.getNumberOfFeatures() == 0) {

      processedFeatureList.removeRow(row);
    }
  }

  public void addAppliedMethodsAndResultToProject() {
    // Add task description to feature list
    originalFeatureList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod(TypeIICorrectionModule.class, parameters,
            getModuleCallDate()));
  }

  @Override
  public double getFinishedPercentage() {
    return totalRows == 0 ? 0 : finished / (double) totalRows;
  }
}


