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
import com.mysql.jdbc.RowData;
import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.compoundannotations.FeatureAnnotation;
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
  private int finished;
  private int totalRows;
  private final double mergeWidth = 0.005;
  private int rtMismatch = 0;
  private int missingFormula = 0;

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

          missingFormula++;
          continue;
        }

        //Converting the molecular formula string to an IMolecularFormula object.
        IMolecularFormula molecularFormula = MolecularFormulaManipulator.getMolecularFormula(
            annotation.getFormula(), SilentChemObjectBuilder.getInstance());
        try {
          annotation.getAdductType().addToFormula(molecularFormula);
        } catch (CloneNotSupportedException e) {
          throw new RuntimeException(e);
        }

        //Calculating the predicted isotope pattern from the features formula.
        final IsotopePattern isotopePattern = IsotopePatternCalculator.calculateIsotopePattern(
            molecularFormula, 0.01, mergeWidth, Math.abs(annotation.getAdductType().getCharge()),
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
          if (!checkIfRtsMatch(monoisotopicEic, monoisotopicFeatureLowerIndex,
              monoisotopicFeatureUpperIndex, overlapEic, overlapFeatureLowerIndex)) {

            rtMismatch++;
            continue;
          }

          /**
           //The indices of both features aligned by retention time.
           int[][] alignedIndices = getAlignedIndices(monoisotopicEic, monoisotopicFeatureLowerIndex,
           monoisotopicFeatureUpperIndex, overlapEic, overlapFeatureLowerIndex,
           overlapFeatureUpperIndex);
           */

          //Calculating the relative intensity of the monoisotopic features isotope.
          IsotopePattern relativeIsotopePattern = isotopePattern.getRelativeIntensityCopy();
          final double relativeIsotopeIntensity = relativeIsotopePattern.getIntensityValue(
              isotopePatternIndex);

          /**
           double[] correctedIntensities = subtractIsootpeIntensity(monoisotopicEic, overlapEic,
           alignedIndices, relativeIsotopeIntensity);
           */

          double[] correctedIntensities = subtractIsotopeIntensities(monoisotopicEic,
              monoisotopicFeatureLowerIndex, overlapEic, overlapFeatureLowerIndex,
              overlapFeatureUpperIndex, relativeIsotopeIntensity);

          //Getting overlap feature mz values.
          double[] overlapFeatureMzs = overlapEic.getMzValues(
              new double[overlapEic.getNumberOfValues()]);

          //Writing the new intensities to the feature.
          IonTimeSeries<? extends Scan> correctedOverlapEic = (IonTimeSeries<? extends Scan>) overlapEic.copyAndReplace(
              getMemoryMapStorage(), overlapFeatureMzs, correctedIntensities);
          overlapFeature.set(FeatureDataType.class, correctedOverlapEic);

          logger.info("Changed feature of m/z: " + overlapFeature.getMZ());

        }

        // Update progress
        finished++;
      }
    }

    OriginalFeatureListOption.KEEP.reflectNewFeatureListToProject("typeII", project,
        processedFeatureList, originalFeatureList);

    logger.info("Total fetaures: " + totalRows);
    logger.info("Times retention times mismatched: " + rtMismatch);
    logger.info("Missing Formulas: " + missingFormula);

    // add to project
    addAppliedMethodsAndResultToProject();

    logger.info("Finished on " + originalFeatureList);
    setStatus(TaskStatus.FINISHED);
  }


  public static int getMatchingIndexInIsotopePattern(IsotopePattern isotopePattern, double mz,
      MZTolerance featureToPatternMzTol) {
    for (int i = 0; i < isotopePattern.getNumberOfDataPoints(); i++) {
      if (featureToPatternMzTol.checkWithinTolerance(mz, isotopePattern.getMzValue(i))) {
        return i;
      }
    }
    return -1;
  }


  public static int getIndexForRt(IonTimeSeries<? extends Scan> eic, float rt) {

    for (int i = 0; i < eic.getNumberOfValues(); i++) {

      if (Math.abs(eic.getRetentionTime(i) - rt) < 0.000001) {
        return i;
      }
    }
    return -1;
  }


  public static boolean checkIfRtsMatch(IonTimeSeries<? extends Scan> monoisotopicEic,
      int monoisotopicFeatureLowerIndex, int monoisotopicFeatureUpperIndex,
      IonTimeSeries<? extends Scan> overlapEic, int overlapFeatureLowerIndex) {

    int monoisotopicIndex = monoisotopicFeatureLowerIndex;
    int overlapIndex = overlapFeatureLowerIndex;

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

    if (overlapEic.getIntensity(overlapFeatueLowerIndex) == 0) {

      correctedIntensities.add(0d);
      monoisotopicIndex++;
      overlapIndex++;
    }

    //Correcting and adding the values during the overlap.
    for (int i = 0; overlapIndex < overlapFeatureUpperIndex; i++) {

      double monoisotopicIntensity = monoisotopicEic.getIntensity(monoisotopicIndex);
      double overlapIntensity = overlapEic.getIntensity(overlapIndex);

      double correctedIntensity =
          overlapIntensity - monoisotopicIntensity * relativeIsotopeIntensity;

      correctedIntensities.add(correctedIntensity);

      monoisotopicIndex++;
      overlapIndex++;
    }

    if (overlapEic.getIntensity(overlapFeatureUpperIndex) == 0) {

      correctedIntensities.add(0d);
      overlapIndex++;
    } else {

      double monoisotopicIntensity = monoisotopicEic.getIntensity(monoisotopicIndex);
      double overlapIntensity = overlapEic.getIntensity(overlapIndex);

      double correctedIntensity =
          overlapIntensity - monoisotopicIntensity * relativeIsotopeIntensity;

      correctedIntensities.add(correctedIntensity);

      overlapIndex++;
    }

    //Adding the values after the overlap.
    for (int i = overlapIndex; i < overlapEic.getNumberOfValues(); i++) {

      correctedIntensities.add(overlapEic.getIntensity(i));
    }

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

/**
 * public static int[][] getAlignedIndices(IonTimeSeries<? extends Scan> monoisotopicEic, int
 * monoisotopicFeatureLowerIndex, int monoisotopicFeatureUpperIndex, IonTimeSeries<? extends Scan>
 * overlapEic, int overlapFeatureLowerIndex, int overlapFeatureUpperIndex) {
 * <p>
 * //Initializing Lists to add the aligned indices to. List<Integer> monoisotopicIndices = new
 * ArrayList<>(); List<Integer> overlapIndices = new ArrayList<>();
 * <p>
 * //Setting the beginning indices for the monoisotopic and the overlapping feature. int
 * monoisotopicIndex = monoisotopicFeatureLowerIndex; int overlapIndex = overlapFeatureLowerIndex;
 * <p>
 * //Adding the beginning indices to the lists. monoisotopicIndices.add(monoisotopicIndex);
 * overlapIndices.add(overlapIndex);
 * <p>
 * //Loop over all the scans within the overlapping region. for (int i = 0; monoisotopicIndex <
 * monoisotopicFeatureUpperIndex && overlapIndex < overlapFeatureUpperIndex; i++) {
 * <p>
 * //Increasing the indices to check the next scans retention times. monoisotopicIndex++;
 * overlapIndex++;
 * <p>
 * //Getting the retention times for the current indices to compare them. float monoisotopicRt =
 * monoisotopicEic.getRetentionTime(monoisotopicIndex); float overlapRt =
 * overlapEic.getRetentionTime(overlapIndex);
 * <p>
 * //Adding both indices to the lists if the retention times are equal. if (Math.abs(monoisotopicRt
 * - overlapRt) < 0.000001) {
 * <p>
 * monoisotopicIndices.add(monoisotopicIndex); overlapIndices.add(overlapIndex); }
 * <p>
 * //Aligning and adding both indices to the lists if there is a jump in the  monoisotopic feature.
 * else if (monoisotopicRt > overlapRt) {
 * <p>
 * int alignedIndex = getIndexForRt(overlapEic, monoisotopicRt);
 * <p>
 * //Adding the aligned indices if they're within the overlap range. if (alignedIndex <=
 * overlapFeatureUpperIndex) {
 * <p>
 * overlapIndex = alignedIndex; monoisotopicIndices.add(monoisotopicIndex);
 * overlapIndices.add(overlapIndex);
 * <p>
 * } else { //Breaking the loop if the index exceeds the overlap range. break; }
 * <p>
 * }
 * <p>
 * //Aligning and adding both indices to the lists if there is a jump in the overlap feature. else
 * {
 * <p>
 * int alignedIndex = getIndexForRt(monoisotopicEic, overlapRt);
 * <p>
 * //Adding the aligned indices if they're within the overlap range. if (alignedIndex <=
 * monoisotopicFeatureUpperIndex) {
 * <p>
 * monoisotopicIndex = alignedIndex; monoisotopicIndices.add(monoisotopicIndex);
 * overlapIndices.add(overlapIndex);
 * <p>
 * } else { //Breaking the loop if the index exceeds the overlap area. break; } } }
 * <p>
 * int[][] alignedIndices = removeDatatpointsWithoutIntensity(monoisotopicEic, overlapEic,
 * monoisotopicIndices, overlapIndices);
 * <p>
 * return alignedIndices; }
 * <p>
 * <p>
 * public static int[][] removeDatatpointsWithoutIntensity( IonTimeSeries<? extends Scan>
 * monoisotopicEic, IonTimeSeries<? extends Scan> overlapEic, List<Integer> monoisotopicIndices,
 * List<Integer> overlapIndices) {
 * <p>
 * List<Integer> filteredMonoisotopicIndices = new ArrayList<>(); List<Integer>
 * filteredOverlapIndices = new ArrayList<>();
 * <p>
 * int startMapIndex = 0; int maxMapIndex = monoisotopicIndices.size() - 1;
 * <p>
 * if (monoisotopicIndices.get(0) == 0 || overlapIndices.get(0) == 0) {
 * <p>
 * filteredMonoisotopicIndices.add(monoisotopicIndices.get(0));
 * filteredOverlapIndices.add(overlapIndices.get(0));
 * <p>
 * startMapIndex = 1; }
 * <p>
 * for (int i = startMapIndex; i < maxMapIndex; i++) {
 * <p>
 * int monoisotopicIndex = monoisotopicIndices.get(i); int overlapIndex = overlapIndices.get(i);
 * <p>
 * if (monoisotopicEic.getIntensity(monoisotopicIndex) != 0 && overlapEic.getIntensity(overlapIndex)
 * != 0) {
 * <p>
 * filteredMonoisotopicIndices.add(monoisotopicIndex); filteredOverlapIndices.add(overlapIndex);
 * <p>
 * } }
 * <p>
 * int monoisotopicIndicesLastIndex = monoisotopicIndices.get(monoisotopicIndices.size() - 1); int
 * monoisotopicFeatureLastIndex = monoisotopicEic.getNumberOfValues() - 1;
 * <p>
 * int overlapIndicesLastIndex = overlapIndices.get(overlapIndices.size() - 1); int
 * overlapFeatureLastIndex = overlapEic.getNumberOfValues() - 1;
 * <p>
 * if (monoisotopicIndicesLastIndex == monoisotopicFeatureLastIndex || overlapIndicesLastIndex ==
 * overlapFeatureLastIndex) {
 * <p>
 * filteredMonoisotopicIndices.add(monoisotopicIndicesLastIndex);
 * filteredOverlapIndices.add(overlapIndicesLastIndex);
 * <p>
 * } else if (monoisotopicEic.getIntensity(monoisotopicIndicesLastIndex) != 0 &&
 * overlapEic.getIntensity(overlapIndicesLastIndex) != 0) {
 * <p>
 * filteredMonoisotopicIndices.add(monoisotopicIndicesLastIndex);
 * filteredOverlapIndices.add(overlapIndicesLastIndex);
 * <p>
 * }
 * <p>
 * int[][] filteredIndices = new int[2][filteredMonoisotopicIndices.size()];
 * <p>
 * for (int i = 0; i < filteredMonoisotopicIndices.size(); i++) {
 * <p>
 * filteredIndices[0][i] = filteredMonoisotopicIndices.get(i); filteredIndices[1][i] =
 * filteredOverlapIndices.get(i);
 * <p>
 * }
 * <p>
 * return filteredIndices; }
 * <p>
 * public static double[] subtractIsotopeIntensities(IonTimeSeries<? extends Scan> monoisotopicEic,
 * IonTimeSeries<? extends Scan> overlapEic, int[][] alignedIndices, double
 * relativeIsotopeIntensity) {
 * <p>
 * List<Double> correctedIntensities = new ArrayList<>();
 * <p>
 * int overlapFeatureLowerIndex = alignedIndices[1][0]; int overlapFeatureUpperIndex =
 * alignedIndices[1][alignedIndices[1].length - 1];
 * <p>
 * //Writing the overlap features intensities before the overlap. for (int i = 0; i <
 * overlapFeatureLowerIndex; i++) { correctedIntensities.add(overlapEic.getIntensity(i)); }
 * <p>
 * //Subtracting the isotope intensities during the overlap. for (int i = 0; i <=
 * alignedIndices[0].length; i++) {
 * <p>
 * int monoisotopicIndex = alignedIndices[0][i]; int overlapIndex = alignedIndices[1][i];
 * <p>
 * double correctedIntensity = overlapEic.getIntensity(overlapIndex) -
 * monoisotopicEic.getIntensity(monoisotopicIndex) * relativeIsotopeIntensity;
 * <p>
 * correctedIntensities.add(correctedIntensity); }
 * <p>
 * //Writing the overlap features intensities after the overlap. for (int i =
 * overlapFeatureUpperIndex + 1; i < overlapEic.getNumberOfValues(); i++) {
 * correctedIntensities.add(overlapEic.getIntensity(i)); }
 * <p>
 * double[] output = new double[correctedIntensities.size()];
 * <p>
 * for (int i = 0; i < correctedIntensities.size(); i++) { output[i] = correctedIntensities.get(i);
 * }
 * <p>
 * return output; }
 * <p>
 * public static double[] subtractIsotopeIntensities(IonTimeSeries<? extends Scan> monoisotopicEic,
 * IonTimeSeries<? extends Scan> overlapEic, int[][] alignedIndices, double
 * relativeIsotopeIntensity) {
 * <p>
 * List<Double> correctedIntensities = new ArrayList<>();
 * <p>
 * int overlapFeatureLowerIndex = alignedIndices[1][0]; int overlapFeatureUpperIndex =
 * alignedIndices[1][alignedIndices[1].length - 1];
 * <p>
 * //Writing the overlap features intensities before the overlap. for (int i = 0; i <
 * overlapFeatureLowerIndex; i++) { correctedIntensities.add(overlapEic.getIntensity(i)); }
 * <p>
 * //Subtracting the isotope intensities during the overlap. for (int i = 0; i <=
 * alignedIndices[0].length; i++) {
 * <p>
 * int monoisotopicIndex = alignedIndices[0][i]; int overlapIndex = alignedIndices[1][i];
 * <p>
 * double correctedIntensity = overlapEic.getIntensity(overlapIndex) -
 * monoisotopicEic.getIntensity(monoisotopicIndex) * relativeIsotopeIntensity;
 * <p>
 * correctedIntensities.add(correctedIntensity); }
 * <p>
 * //Writing the overlap features intensities after the overlap. for (int i =
 * overlapFeatureUpperIndex + 1; i < overlapEic.getNumberOfValues(); i++) {
 * correctedIntensities.add(overlapEic.getIntensity(i)); }
 * <p>
 * double[] output = new double[correctedIntensities.size()];
 * <p>
 * for (int i = 0; i < correctedIntensities.size(); i++) { output[i] = correctedIntensities.get(i);
 * }
 * <p>
 * return output; }
 */

/**
 public static double[] subtractIsotopeIntensities(IonTimeSeries<? extends Scan> monoisotopicEic,
 IonTimeSeries<? extends Scan> overlapEic, int[][] alignedIndices,
 double relativeIsotopeIntensity) {

 List<Double> correctedIntensities = new ArrayList<>();

 int overlapFeatureLowerIndex = alignedIndices[1][0];
 int overlapFeatureUpperIndex = alignedIndices[1][alignedIndices[1].length - 1];

 //Writing the overlap features intensities before the overlap.
 for (int i = 0; i < overlapFeatureLowerIndex; i++) {
 correctedIntensities.add(overlapEic.getIntensity(i));
 }

 //Subtracting the isotope intensities during the overlap.
 for (int i = 0; i <= alignedIndices[0].length; i++) {

 int monoisotopicIndex = alignedIndices[0][i];
 int overlapIndex = alignedIndices[1][i];

 double correctedIntensity = overlapEic.getIntensity(overlapIndex)
 - monoisotopicEic.getIntensity(monoisotopicIndex) * relativeIsotopeIntensity;

 correctedIntensities.add(correctedIntensity);
 }

 //Writing the overlap features intensities after the overlap.
 for (int i = overlapFeatureUpperIndex + 1; i < overlapEic.getNumberOfValues(); i++) {
 correctedIntensities.add(overlapEic.getIntensity(i));
 }

 double[] output = new double[correctedIntensities.size()];

 for (int i = 0; i < correctedIntensities.size(); i++) {
 output[i] = correctedIntensities.get(i);
 }

 return output;
 }
 */