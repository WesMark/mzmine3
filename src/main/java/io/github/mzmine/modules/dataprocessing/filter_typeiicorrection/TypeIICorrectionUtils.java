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

  public static int getMatchingIndexInIsotopePattern(IsotopePattern isotopePattern, double mz,
      MZTolerance featureToPatternMzTol) {

    //Going over all mzs in the isotope pattern until an isotope mz matches the wanted mz.
    //Beginning with i = 1, because i = 0 corresponds to the monoisotopic signal.
    for (int i = 1; i < isotopePattern.getNumberOfDataPoints(); i++) {
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
    while (monoisotopicIndex <= monoisotopicFeatureUpperIndex) {

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


  public static IsotopePattern getIsotopePatternForFeature(Feature feature, double mergeWidth) {

    //Getting the annotation for the feature.
    //Continuing with the next row, if there is no annotation, formula or adduct type for this feature.
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


  public static double[] subtractIsotopeIntensities(IonTimeSeries<? extends Scan> monoisotopicEic,
      int monoisotopicFeatureLowerIndex, IonTimeSeries<? extends Scan> overlapEic,
      int overlapFeatueLowerIndex, int overlapFeatureUpperIndex, double relativeIsotopeIntensity) {

    List<Double> correctedIntensities = new ArrayList<>();

    //Counter for the overlap index.
    int overlapIndex = 0;

    //Adding values before the overlap.
    while (overlapIndex < overlapFeatueLowerIndex) {

      correctedIntensities.add(overlapEic.getIntensity(overlapIndex));
      overlapIndex++;
    }

    //Counter for the monoisotopic index.
    int monoisotopicIndex = monoisotopicFeatureLowerIndex;

    //Correcting and adding the values during the overlap.
    while (overlapIndex <= overlapFeatureUpperIndex) {

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
    while (overlapIndex < overlapEic.getNumberOfValues()) {

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
}
