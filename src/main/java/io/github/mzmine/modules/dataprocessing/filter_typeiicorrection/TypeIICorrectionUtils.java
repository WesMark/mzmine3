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

import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.compoundannotations.FeatureAnnotation;
import io.github.mzmine.modules.tools.isotopeprediction.IsotopePatternCalculator;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.FeatureUtils;
import java.util.ArrayList;
import java.util.List;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class TypeIICorrectionUtils {

  /**
   * Searches for a given m/z-value in an isotope pattern and returns the m/z-values index in the
   * isotope pattern.
   *
   * @param isotopePattern        The isotope pattern the m/z-value will be searched in.
   * @param mz                    The m/z-value that will be searched in the isotope pattern.
   * @param featureToPatternMzTol The accepted tolerance between the given m/z-value and the one
   *                              found in the isotope pattern.
   * @return Returns the index of the entered m/z-value in the isotope pattern. Returns -1 if the
   * isotope pattern doesn't contain the entered m/z-value.
   */
  public static int getMatchingIndexInIsotopePattern(IsotopePattern isotopePattern, double mz,
      MZTolerance featureToPatternMzTol) {

    //Going over all mzs in the isotope pattern until an isotope mz matches the wanted mz.
    //Beginning with i = 1, because i = 0 corresponds to the monoisotopic signal.
    for (int i = 1; i < isotopePattern.getNumberOfDataPoints(); i++) {

      //Returning the index if the mz-values match.
      if (featureToPatternMzTol.checkWithinTolerance(mz, isotopePattern.getMzValue(i))) {
        return i;
      }
    }

    //Returning -1 if no matching mz is found.
    return -1;
  }

  /**
   * Searches for the index of a scan of a given retention time in an EIC.
   *
   * @param eic The EIC the retention time will be searched in.
   * @param rt  The retention time that will be searched.
   * @return Returns the index of the scan for the given retention time. Returns -1 if no scan is
   * found for the given retention time.
   */
  public static int getIndexForRt(IonTimeSeries<? extends Scan> eic, float rt) {

    //Going over all scans in the eic until the scans rt matches the wanted rt.
    for (int i = 0; i < eic.getNumberOfValues(); i++) {

      //Returning the index if the rts match.
      if (Math.abs(eic.getRetentionTime(i) - rt) < 0.000001) {
        return i;
      }
    }

    //Returning -1 if no matching rt is found.
    return -1;
  }

  /**
   * Checks if the retention times of two EICs match index by index in a given overlap range.
   *
   * @param eic1                  EIC #1.
   * @param eic1LowerOverlapIndex The index for EIC #1 where the overlap with EIC #2 beginns.
   * @param eic1UpperOverlapIndex The index for EIC #2 where the overlap with EIC #2 ends.
   * @param eic2                  EIC #2.
   * @param eic2lowerOverlapIndex The index for EIC #2 where the overlap with EIC #1 begins.
   * @return Returns true if the retention times for the corresponding indices are the same in the
   * overlap range. Returns false if one or more retention times differ for a corresponding pair of
   * indices.
   */
  public static boolean rtsMatching(IonTimeSeries<? extends Scan> eic1, int eic1LowerOverlapIndex,
      int eic1UpperOverlapIndex, IonTimeSeries<? extends Scan> eic2, int eic2lowerOverlapIndex) {

    //Initializing the indices.
    int monoisotopicIndex = eic1LowerOverlapIndex;
    int overlapIndex = eic2lowerOverlapIndex;

    //Going over every index pair of both features and checking weather the rts are the same.
    while (monoisotopicIndex <= eic1UpperOverlapIndex) {

      float monoisotopicRt = eic1.getRetentionTime(monoisotopicIndex);
      float overlapRt = eic2.getRetentionTime(overlapIndex);

      //Returning false if the rts don't match.
      if (Math.abs(monoisotopicRt - overlapRt) > 0.00001f) {
        return false;
      }

      //Incrementing the indices.
      monoisotopicIndex++;
      overlapIndex++;
    }

    //Returning true if all the rts matched.
    return true;
  }

  /**
   * Calculates the isotope pattern for a given feature. The feature needs to contain sufficient
   * annotation data.
   *
   * @param feature    The feature the isotope pattern will be calculated for.
   * @param mergeWidth The merge width for the calculated isotope pattern
   * @return Returns the calculated isotope pattern for the charged molecule. Returns null if the
   * feature annotation contains no formula and/or adduct type.
   */
  public static IsotopePattern getIsotopePatternForFeature(Feature feature, double mergeWidth) {

    //Getting the annotation for the feature.
    //Returning null, if there is no annotation, formula or adduct type for the feature.
    FeatureAnnotation annotation = FeatureUtils.getBestFeatureAnnotation(feature.getRow());
    if (annotation == null || annotation.getFormula() == null
        || annotation.getAdductType() == null) {
      return null;
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
    return IsotopePatternCalculator.calculateIsotopePattern(chargedFormula, 0.01, mergeWidth,
        Math.abs(annotation.getAdductType().getCharge()),
        feature.getRepresentativeScan().getPolarity(), false);
  }

  /**
   * Subtracts the intensity data for an overlap caused by a features isotope signal.
   *
   * @param monoisotopicEic               The EIC of the feature that causes the overlap.
   * @param monoisotopicFeatureLowerIndex The index where the overlap starts for the feature that
   *                                      causes the overlap.
   * @param relativeIsotopeIntensity      The relative isotope intensity for the feature that causes
   *                                      the overlap.
   * @param overlapEic                    The EIC of the feature that is overlapped by the isotopes
   *                                      signal.
   * @param overlapFeatueLowerIndex       The index where the overlap starts for the feature that is
   *                                      overlapped.
   * @param overlapFeatureUpperIndex      The index where the overlap ends for the feature that is
   *                                      overlapped.
   * @return Returns a double array with the features corrected intensity data.
   */
  public static double[] subtractIsotopeIntensities(IonTimeSeries<? extends Scan> monoisotopicEic,
      int monoisotopicFeatureLowerIndex, double relativeIsotopeIntensity,
      IonTimeSeries<? extends Scan> overlapEic, int overlapFeatueLowerIndex,
      int overlapFeatureUpperIndex) {

    //Initializing a List for the corrected intensities to be added to.
    final List<Double> correctedIntensities = new ArrayList<>();

    //Counter for the index of the overlap feature.
    int overlapIndex = 0;

    //Adding values before the overlap.
    while (overlapIndex < overlapFeatueLowerIndex) {

      correctedIntensities.add(overlapEic.getIntensity(overlapIndex));
      overlapIndex++;
    }

    //Counter for the index of the monoisotopic feature.
    int monoisotopicIndex = monoisotopicFeatureLowerIndex;

    //Correcting and adding the values during the overlap.
    while (overlapIndex <= overlapFeatureUpperIndex) {

      double monoisotopicIntensity = monoisotopicEic.getIntensity(monoisotopicIndex);
      double overlapIntensity = overlapEic.getIntensity(overlapIndex);

      //Calculating the corrected intensity.
      double correctedIntensity =
          overlapIntensity - monoisotopicIntensity * relativeIsotopeIntensity;

      //Setting the intensity value to 0 if the calculated intensity is negative
      if (correctedIntensity < 0) {
        correctedIntensity = 0d;
      }

      //Adding the corrected intensity to the List of intensities.
      correctedIntensities.add(correctedIntensity);

      //Incrementing the indices.
      monoisotopicIndex++;
      overlapIndex++;
    }

    //Adding the values after the overlap.
    while (overlapIndex < overlapEic.getNumberOfValues()) {

      correctedIntensities.add(overlapEic.getIntensity(overlapIndex));
      overlapIndex++;
    }

    //Writing the corrected intensities to an array.
    final double[] outputArray = new double[correctedIntensities.size()];

    for (int i = 0; i < correctedIntensities.size(); i++) {

      outputArray[i] = correctedIntensities.get(i);
    }

    return outputArray;
  }
}
