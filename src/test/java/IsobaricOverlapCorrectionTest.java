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

import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.IsotopePattern.IsotopePatternStatus;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonTimeSeries;
import io.github.mzmine.datamodel.impl.SimpleIsotopePattern;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.modules.dataprocessing.filter_typeiicorrection.TypeIICorrectionUtils;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.impl.RawDataFileImpl;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IsobaricOverlapCorrectionTest {

  public List<Scan> makeSomeScans(RawDataFile file, int numScans, float rtFactor, float rtShift) {

    List<Scan> scans = new ArrayList<>();

    //Creating scans with just the rts added to them.
    for (int i = 0; i < numScans; i++) {
      SimpleScan scan = new SimpleScan(file, i, 1, i * rtFactor + rtShift, null, null, null, null,
          null, "test", null);

      scans.add(scan);
    }
    return scans;
  }

  public double[] createFilledArray(int arrayLength) {

    //Filling an array of the length arrayLength with the value 1.
    double[] outputArray = new double[arrayLength];
    for (int i = 0; i < arrayLength; i++) {
      outputArray[i] = 1d;
    }
    return outputArray;
  }

  public IonTimeSeries<Scan> createEic(int numScans, float rtSteps, float rtStart, double[] mzs,
      double[] intensities) {

    //Creating arrays for the mz and intensity values.
    double[] mzData;
    double[] intensityData;

    //Filling the mz array with values of 1 if no mz values are passed.
    if (mzs == null) {
      mzData = createFilledArray(numScans);
    } else {
      mzData = mzs;
    }

    //Filling the intensity array with values of 1 if no intensity values are passed.
    if (intensities == null) {
      intensityData = createFilledArray(numScans);
    } else {
      intensityData = intensities;
    }

    //Creating scans for the given rt spacing.
    RawDataFile file = new RawDataFileImpl("file", null, null);
    List<Scan> scans = makeSomeScans(file, numScans, rtSteps, rtStart);

    //Creating the eic from the scans, mz and intenity values.
    return new SimpleIonTimeSeries(null, mzData, intensityData, scans);
  }


  @Test
  void testGetMatchingIndexInIsotopePattern() {

    //Creating an isotope pattern.
    IsotopePattern pattern = new SimpleIsotopePattern(new double[]{1d, 2d, 3d},
        new double[]{1, 1, 1}, 1, IsotopePatternStatus.DETECTED, "test");

    //Testing a value the pattern contains.
    Assertions.assertEquals(1, TypeIICorrectionUtils.getMatchingIndexInIsotopePattern(pattern, 2,
        new MZTolerance(0.1, 0)));

    //Testing a value the pattern doesn't contain.
    Assertions.assertEquals(-1, TypeIICorrectionUtils.getMatchingIndexInIsotopePattern(pattern, 1.5,
        new MZTolerance(0.1, 0)));

    //Testing the tolerance.
    Assertions.assertEquals(2, TypeIICorrectionUtils.getMatchingIndexInIsotopePattern(pattern, 3.1,
        new MZTolerance(0.1, 0)));

    Assertions.assertNotEquals(2,
        TypeIICorrectionUtils.getMatchingIndexInIsotopePattern(pattern, 3.11,
            new MZTolerance(0.1, 0)));
  }

  @Test
  void testGetIndexForRt() {

    //Creating an eic.
    IonTimeSeries<Scan> eic = createEic(30, 0.1f, 0f, null, null);

    //Testing the expected value.
    Assertions.assertEquals(1, TypeIICorrectionUtils.getIndexForRt(eic, 0.1f));

    //Testing a value out of range.
    Assertions.assertEquals(-1, TypeIICorrectionUtils.getIndexForRt(eic, 3.1f));

    //Testing an invalid value.
    Assertions.assertEquals(-1, TypeIICorrectionUtils.getIndexForRt(eic, -0.1f));
  }

  @Test
  void testCheckIfRtMatch() {

    //Creating an eic for the monoisotopic feature.
    IonTimeSeries<Scan> monoisotopicEic = createEic(31, 0.1f, 0f, null, null);

    //Creating an eic for the overlap feature.
    IonTimeSeries<Scan> overlapEic = createEic(31, 0.1f, 1.5f, null, null);

    Assertions.assertTrue(
        TypeIICorrectionUtils.rtsMatching(monoisotopicEic, 15, 30, overlapEic, 0));

    Assertions.assertFalse(
        TypeIICorrectionUtils.rtsMatching(monoisotopicEic, 15, 30, overlapEic, 1));
  }

  @Test
  void testSubtractIsotopeIntensity() {

    //The relative intensity of the isotope.
    double relIsotopeIntensity = 0.5d;

    //Creating an eic for the monoisotopic feature.
    double[] monoisotopicIntensities = new double[]{0, 50, 100, 500, 1_000, 3_000, 7_000, 10_000,
        7_500, 5000, 2000, 900, 700, 500, 10, 0};
    IonTimeSeries<Scan> monoisotopicEic = createEic(16, 0.1f, 0.3f, null, monoisotopicIntensities);

    //Creating three different overlap features.
    double[] overlapIntensities1 = new double[]{0, 1_000, 5_000, 10_000, 4_000, 500};
    IonTimeSeries<Scan> overlapEic1 = createEic(6, 0.1f, 0f, null, overlapIntensities1);

    double[] overlapIntensities2 = new double[]{100, 1_000, 3_000, 5_000, 2_000, 1_000};
    IonTimeSeries<Scan> overlapEic2 = createEic(6, 0.1f, 0.7f, null, overlapIntensities2);

    double[] overlapIntensities3 = new double[]{0, 500, 700, 1_500, 600, 300};
    IonTimeSeries<Scan> overlapEic3 = createEic(6, 0.1f, 1.5f, null, overlapIntensities3);

    //Creating the expected arrays.
    double[] correctedIntensities1 = new double[]{0, 1_000, 5_000, 10_000, 3_975, 450};
    double[] correctedIntensities2 = new double[]{0, 0, 0, 0, 0, 0};
    double[] correctedIntensities3 = new double[]{0, 250, 695, 1_500, 600, 300};

    //Testing a feature that overlaps at the beginning of the monoisotopic feature.
    Assertions.assertArrayEquals(correctedIntensities1,
        TypeIICorrectionUtils.subtractIsotopeIntensities(monoisotopicEic, 0, overlapEic1, 3, 5,
            relIsotopeIntensity));

    //Testing a feature that completely overlaps with the monoisotopic feature.
    Assertions.assertArrayEquals(correctedIntensities2,
        TypeIICorrectionUtils.subtractIsotopeIntensities(monoisotopicEic, 4, overlapEic2, 0, 5,
            relIsotopeIntensity));

    //Testing a feature that overlaps at the end of the monoisotopic feature.
    Assertions.assertArrayEquals(correctedIntensities3,
        TypeIICorrectionUtils.subtractIsotopeIntensities(monoisotopicEic, 12, overlapEic3, 0, 3,
            relIsotopeIntensity));
  }


}