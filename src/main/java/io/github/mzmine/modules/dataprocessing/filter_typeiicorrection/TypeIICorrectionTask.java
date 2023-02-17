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
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.compoundannotations.FeatureAnnotation;
import io.github.mzmine.datamodel.features.types.DetectionType;
import io.github.mzmine.datamodel.features.types.FeatureDataType;
import io.github.mzmine.modules.tools.isotopeprediction.IsotopePatternCalculator;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureListRowSorter;
import io.github.mzmine.util.FeatureUtils;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class TypeIICorrectionTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(TypeIICorrectionTask.class.getName());

  private final ParameterSet parameters;
  private final MZmineProject project;
  private final ModularFeatureList originalFeatureList;
  private ModularFeatureList processedFeatureList;
  private final MZTolerance featureToPatternMzTol = new MZTolerance(0.005, 5);
  // features counter
  private final double mergeWidth = 0.005;
  private int finished;
  private int totalRows;

  //Counter for logging.
  private int rtMismatch = 0;
  private int changedFeatures = 0;
  private int removedFeatures = 0;

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
  }

  @Override
  public String getTaskDescription() {
    return "Runs task on " + originalFeatureList;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    logger.info("Running task on " + originalFeatureList);

    //Setting number of rows to calculate finished percentage.
    totalRows = originalFeatureList.getNumberOfRows();

    //Creating a copy of the used Feature List.
    processedFeatureList = originalFeatureList.createCopy(
        originalFeatureList.getName() + " type2corr", getMemoryMapStorage(), false);

    //Extracting raw data files used in the feature List.
    final List<RawDataFile> rawDataFiles = processedFeatureList.getRawDataFiles();

    //Getting rows from the feature List.
    List<FeatureListRow> rows = processedFeatureList.getRows();
    rows.sort(FeatureListRowSorter.MZ_ASCENDING);

    //Loop going over all the raw data files used in the feature list.
    for (final RawDataFile file : rawDataFiles) {

      //Loop going over all the rows of a given raw data file of the feature list.
      for (int i = 0; i < rows.size() - 1; i++) {

        // check for cancelled state and stop
        if (isCanceled()) {
          return;
        }

        //Getting a row from the list of all rows.
        final FeatureListRow row = rows.get(i);

        //Getting the feature from the row for the given raw data file.
        //Continuing with the next row, if there is no feature for this row in the given raw data file.
        Feature monoisotopicFeature = row.getFeature(file);
        if (monoisotopicFeature == null) {
          continue;
        }

        //Getting the annotation for the feature.
        //Continuing with the next row, if there is no annotation, formula or adduct type for this feature.
        FeatureAnnotation annotation = FeatureUtils.getBestFeatureAnnotation(row);
        if (annotation == null || annotation.getFormula() == null
            || annotation.getAdductType() == null) {
          continue;
        }

        //Converting the molecular formula string for the neutral molecule to an IMolecularFormula object.
        IMolecularFormula molecularFormula = MolecularFormulaManipulator.getMolecularFormula(
            annotation.getFormula(), SilentChemObjectBuilder.getInstance());

        //Adding the adduct to the molecular formula.
        IMolecularFormula chargedFormula;
        try {
          chargedFormula = annotation.getAdductType().addToFormula(molecularFormula);
        } catch (CloneNotSupportedException e) {
          throw new RuntimeException(e);
        }

        //Calculating the predicted isotope pattern from the features charged molecular formula.
        final IsotopePattern isotopePattern = IsotopePatternCalculator.calculateIsotopePattern(
            chargedFormula, 0.01, mergeWidth, Math.abs(annotation.getAdductType().getCharge()),
            monoisotopicFeature.getRepresentativeScan().getPolarity(), false);

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
          int isotopePatternIndex = getMatchingIndexInIsotopePattern(isotopePattern,
              overlapFeature.getMZ(), featureToPatternMzTol);
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
          int monoisotopicFeatureLowerIndex = getIndexForRt(monoisotopicEic, lowerRtOverlap);
          int monoisotopicFeatureUpperIndex = getIndexForRt(monoisotopicEic, upperRtOverlap);

          //The indices for the overlap region of the overlap feature.
          int overlapFeatureLowerIndex = getIndexForRt(overlapEic, lowerRtOverlap);
          int overlapFeatureUpperIndex = getIndexForRt(overlapEic, upperRtOverlap);

          //Checking weather there are jumps in the retention times between the two features.
          //Continuing with the next overlap feature if there are.
          if (!rtsMatching(monoisotopicEic, monoisotopicFeatureLowerIndex,
              monoisotopicFeatureUpperIndex, overlapEic, overlapFeatureLowerIndex)) {

            //Increasing the counter for logging the number of times the rts didn't match.
            rtMismatch++;
            continue;
          }

          //Calculating the relative intensity of the monoisotopic features isotope.
          IsotopePattern relativeIsotopePattern = isotopePattern.getRelativeIntensityCopy();
          final double relativeIsotopeIntensity = relativeIsotopePattern.getIntensityValue(
              isotopePatternIndex);

          double[] correctedIntensities = subtractIsotopeIntensities(monoisotopicEic,
              monoisotopicFeatureLowerIndex, overlapEic, overlapFeatureLowerIndex,
              overlapFeatureUpperIndex, relativeIsotopeIntensity);

          //Getting overlap feature mz values.
          double[] overlapFeatureMzs = overlapEic.getMzValues(
              new double[overlapEic.getNumberOfValues()]);

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

          //Boolean variable for logging weather a feature was removed.
          boolean featureRemoved = false;

          //Removing a feature if its corrected area is below 100.
          if (overlapFeature.getArea() < 100) {

            followingRow.removeFeature(overlapFeature.getRawDataFile());
            removedFeatures++;

            featureRemoved = true;
          }

          //Removing a row if it doesn't contain any features anymore.
          if (followingRow.getNumberOfFeatures() == 0) {

            processedFeatureList.removeRow(followingRow);
          }

          //Increasing the counter for logging the total number of changed features.
          changedFeatures++;

          //Logging information of the changed features.
          logger.info("Overlap feature ID: " + overlapFeature.getRow().getID()
              + "\n Monoisotopic Feature ID: " + monoisotopicFeature.getRow().getID()
              + "\n Area changed from " + overlapFeatureArea + " to " + correctedOverlapFeatureArea
              + "\n Feature was removed: " + featureRemoved);
        }

        // Update progress
        finished++;
      }
    }

    //Keeping the original feature list and showing the processed one in the project.
    OriginalFeatureListOption.KEEP.reflectNewFeatureListToProject("typeII", project,
        processedFeatureList, originalFeatureList);

    //Logging statistics.
    logger.info(
        "Times retention times mismatched: " + rtMismatch + "\n Number of changed features: "
            + changedFeatures + "\n Number of removed features: " + removedFeatures);

    // add to project
    addAppliedMethodsAndResultToProject();

    logger.info("Finished on " + originalFeatureList);
    setStatus(TaskStatus.FINISHED);
  }


  public static int getMatchingIndexInIsotopePattern(IsotopePattern isotopePattern, double mz,
      MZTolerance featureToPatternMzTol) {

    //Going over all mzs in the isotope pattern until an isotope mz matches the wanted mz.
    for (int i = 0; i < isotopePattern.getNumberOfDataPoints(); i++) {
      if (featureToPatternMzTol.checkWithinTolerance(mz, isotopePattern.getMzValue(i))) {
        return i;
      }
    }

    //Returning -1 if no matching mz is found.
    return -1;
  }


  public static int getIndexForRt(IonTimeSeries<? extends Scan> eic, float rt) {

    //Going over all scans in the eic until the scans rt matches the wanted rt.
    for (int i = 0; i < eic.getNumberOfValues(); i++) {

      if (Math.abs(eic.getRetentionTime(i) - rt) < 0.000001) {
        return i;
      }
    }

    //Returning -1 if no matching rt is found.
    return -1;
  }


  public static boolean rtsMatching(IonTimeSeries<? extends Scan> monoisotopicEic,
      int monoisotopicFeatureLowerIndex, int monoisotopicFeatureUpperIndex,
      IonTimeSeries<? extends Scan> overlapEic, int overlapFeatureLowerIndex) {

    int monoisotopicIndex = monoisotopicFeatureLowerIndex;
    int overlapIndex = overlapFeatureLowerIndex;

    //Going over every index pair of both features and checking weather the rts are the same.
    for (int i = 0; monoisotopicIndex <= monoisotopicFeatureUpperIndex; i++) {

      float monoisotopicRt = monoisotopicEic.getRetentionTime(monoisotopicIndex);
      float overlapRt = overlapEic.getRetentionTime(overlapIndex);

      if (Math.abs(monoisotopicRt - overlapRt) > 0.00001f) {
        return false;
      }

      monoisotopicIndex++;
      overlapIndex++;
    }

    return true;
  }

  public static double[] subtractIsotopeIntensities(IonTimeSeries<? extends Scan> monoisotopicEic,
      int monoisotopicFeatureLowerIndex, IonTimeSeries<? extends Scan> overlapEic,
      int overlapFeatueLowerIndex, int overlapFeatureUpperIndex, double relativeIsotopeIntensity) {

    List<Double> correctedIntensities = new ArrayList<>();

    //Adding values before the overlap.
    for (int i = 0; i < overlapFeatueLowerIndex; i++) {

      correctedIntensities.add(overlapEic.getIntensity(i));
    }

    int monoisotopicIndex = monoisotopicFeatureLowerIndex;
    int overlapIndex = overlapFeatueLowerIndex;

    //Correcting and adding the values during the overlap.
    for (int i = 0; overlapIndex <= overlapFeatureUpperIndex; i++) {

      double monoisotopicIntensity = monoisotopicEic.getIntensity(monoisotopicIndex);
      double overlapIntensity = overlapEic.getIntensity(overlapIndex);

      double correctedIntensity =
          overlapIntensity - monoisotopicIntensity * relativeIsotopeIntensity;

      //Setting the intensity value to 0 if the calculated intensity is negative
      if (correctedIntensity < 0) {
        correctedIntensity = 0d;
      }

      correctedIntensities.add(correctedIntensity);

      monoisotopicIndex++;
      overlapIndex++;
    }

    //Adding the values after the overlap.
    for (int i = 0; overlapIndex < overlapEic.getNumberOfValues(); i++) {

      correctedIntensities.add(overlapEic.getIntensity(overlapIndex));
      overlapIndex++;
    }

    //Writing the corrected intensities to an array.
    double[] outputArray = new double[correctedIntensities.size()];
    for (int i = 0; i < correctedIntensities.size(); i++) {

      outputArray[i] = correctedIntensities.get(i);
    }

    return outputArray;
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


