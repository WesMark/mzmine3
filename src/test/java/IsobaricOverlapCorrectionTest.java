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

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.IsotopePattern.IsotopePatternStatus;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonTimeSeries;
import io.github.mzmine.datamodel.impl.SimpleIsotopePattern;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.modules.dataprocessing.filter_typeiicorrection.TypeIICorrectionTask;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.impl.RawDataFileImpl;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IsobaricOverlapCorrectionTest {

  public List<Scan> makeSomeScans(RawDataFile file, int numScans, float rtFactor, float rtShift) {

    List<Scan> scans = new ArrayList<>();

    for (int i = 0; i < numScans; i++) {
      SimpleScan scan = new SimpleScan(file, i, 1, i * rtFactor + rtShift, null,
          new double[]{0d, 1}, new double[]{15d, 1E5}, MassSpectrumType.CENTROIDED,
          PolarityType.POSITIVE, "test", Range.closed(0d, 1d));

      scans.add(scan);
    }
    return scans;
  }

  public double[] createFilledArray(int arrayLength) {

    double[] outputArray = new double[arrayLength];
    for (int i = 0; i < arrayLength; i++) {
      outputArray[i] = 1d;
    }
    return outputArray;
  }

  public IonTimeSeries<Scan> createEic(int numScans, float rtSteps, float rtStart, double[] mzs,
      double[] intensities) {

    double[] mzData;
    double[] intensityData;

    if (mzs == null) {
      mzData = createFilledArray(numScans);
    } else {
      mzData = mzs;
    }

    if (intensities == null) {
      intensityData = createFilledArray(numScans);
    } else {
      intensityData = intensities;
    }

    RawDataFile file = new RawDataFileImpl("file", null, null);
    List<Scan> scans = makeSomeScans(file, numScans, rtSteps, rtStart);
    IonTimeSeries<Scan> eic = new SimpleIonTimeSeries(null, mzData, intensityData, scans);

    return eic;
  }


  @Test
  void testGetMatchingIndexInIsotopePattern() {

    //Creating an isotope pattern.
    IsotopePattern pattern = new SimpleIsotopePattern(new double[]{1d, 2d, 3d},
        new double[]{1, 1, 1}, 1, IsotopePatternStatus.DETECTED, "");

    //Testing a value the pattern contains.
    Assertions.assertEquals(1,
        TypeIICorrectionTask.getMatchingIndexInIsotopePattern(pattern, 2, new MZTolerance(0.1, 0)));

    //Testing a value the pattern doesn't contain.
    Assertions.assertEquals(-1, TypeIICorrectionTask.getMatchingIndexInIsotopePattern(pattern, 1.5,
        new MZTolerance(0.1, 0)));

    //Testing the tolerance.
    Assertions.assertEquals(2, TypeIICorrectionTask.getMatchingIndexInIsotopePattern(pattern, 3.1,
        new MZTolerance(0.1, 0)));

    Assertions.assertNotEquals(2,
        TypeIICorrectionTask.getMatchingIndexInIsotopePattern(pattern, 3.11,
            new MZTolerance(0.1, 0)));
  }

  @Test
  void testGetIndexForRt() {

    //Creating an eic.
    RawDataFile file = new RawDataFileImpl("file", null, null);
    final int numScans = 30;
    List<Scan> scans = makeSomeScans(file, numScans, 0.1f, 0f);
    IonTimeSeries<Scan> eic = new SimpleIonTimeSeries(null, createFilledArray(numScans),
        createFilledArray(numScans), scans);

    //Testing the expected value.
    Assertions.assertEquals(1, TypeIICorrectionTask.getIndexForRt(eic, 0.1f));

    //Testing a value out of range.
    Assertions.assertEquals(-1, TypeIICorrectionTask.getIndexForRt(eic, 3.1f));

    //Testing an invalid value.
    Assertions.assertEquals(-1, TypeIICorrectionTask.getIndexForRt(eic, -0.1f));
  }

  @Test
  void testCheckIfRtMatch() {

    //Creating an eic for the monoisotopic feature.
    IonTimeSeries<Scan> monoisotopicEic = createEic(31, 0.1f, 0f, null, null);

    //Creating an eic for the overlap feature.
    IonTimeSeries<Scan> overlapEic = createEic(31, 0.1f, 1.5f, null, null);

    Assertions.assertTrue(
        TypeIICorrectionTask.checkIfRtsMatch(monoisotopicEic, 15, 30, overlapEic, 0));

    Assertions.assertFalse(
        TypeIICorrectionTask.checkIfRtsMatch(monoisotopicEic, 15, 30, overlapEic, 1));
  }

  @Test
  void testSubtractIsotopeIntensity() {

    //Creating an eic for the monoisotopic feature.
    double[] monoisotopicIntensities = new double[]{0, 50, 100, 500, 1_000, 3_000, 7_000, 10_000,
        7_500, 5000, 2000, 900, 700, 500, 10, 0};
    IonTimeSeries<Scan> monoisotopicEic = createEic(16, 0.1f, 0.3f, null, monoisotopicIntensities);

    //Creating three different overlap features.
    double[] overlapIntensities1 = new double[]{0, 500, 1_000, 700, 100, 0};
    IonTimeSeries<Scan> overlapEic1 = createEic(6, 0.1f, 0f, null, overlapIntensities1);

    double[] overlapIntensities2 = new double[]{0, 3000, 70_000, 100_000, 50_000, 0};
    IonTimeSeries<Scan> overlapEic2 = createEic(6, 0.1f, 0.7f, null, overlapIntensities2);

    double[] overlapIntensities3 = new double[]{0, 7_000, 10_000, 5_000, 1_000, 0};
    IonTimeSeries<Scan> overlapEic3 = createEic(6, 0.1f, 1.5f, null, overlapIntensities3);

    //Creating the expected arrays.
    double[] correctedIntensities1 = new double[]{0, 500, 1_000, 700, 75, 0};
    double[] correctedIntensities2 = new double[]{0, 1_500, 66_500, 95_000, 46_250, 0};
    double[] correctedIntensities3 = new double[]{0, 6_750, 9_995, 5_000, 1_000, 0};

    Assertions.assertArrayEquals(correctedIntensities1,
        TypeIICorrectionTask.subtractIsotopeIntensities(monoisotopicEic, 0, overlapEic1, 3, 5,
            0.5));

    Assertions.assertArrayEquals(correctedIntensities2,
        TypeIICorrectionTask.subtractIsotopeIntensities(monoisotopicEic, 4, overlapEic2, 0, 5,
            0.5));

    Assertions.assertArrayEquals(correctedIntensities3,
        TypeIICorrectionTask.subtractIsotopeIntensities(monoisotopicEic, 12, overlapEic3, 0, 3,
            0.5));
  }

  /**
   @Test void testGetAlignedIndices() {

   //Creating an eic for the monoisotopic feature.
   RawDataFile monoisotopicFile = new RawDataFileImpl("file", null, null);
   List<Scan> monoisotopicScans = makeSomeScans(monoisotopicFile, 21, 0.1f, 0f);
   IonTimeSeries<Scan> monoisotopicEic = new SimpleIonTimeSeries(null,
   makeFilledArray(21), makeFilledArray(21),  monoisotopicScans);

   //Checking the method for an eic that contains one jump in retention time in the overlap feature.
   RawDataFile oneJumpFile = new RawDataFileImpl("file", null, null);
   List<Scan> oneJumpScans = makeSomeScans(oneJumpFile, 3, 0.1f, 0.5f);
   oneJumpScans.addAll(makeSomeScans(oneJumpFile, 8, 0.1f, 1.3f));
   IonTimeSeries<Scan> oneJumpEic = new SimpleIonTimeSeries(null, makeFilledArray(11),
   new double[]{1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1}, oneJumpScans);

   int[][] expectedDataOneStop = new int[][]{{5, 6, 14, 15, 16, 17, 18, 19, 20},
   {0, 1, 4, 5, 6, 7, 8, 9, 10}};

   Assertions.assertArrayEquals(expectedDataOneStop,
   TypeIICorrectionTask.getAlignedIndices(monoisotopicEic, 5, 20,
   oneJumpEic, 0, 10));


   //Checking the method for an eic that contains one jump in retention time in the monoisotopic feature.
   int[][] expectedDataOneStopInverted = new int[][]{{0, 1, 4, 5, 6, 7, 8, 9, 10}, {5, 6, 14, 15, 16, 17, 18, 19, 20}};

   Assertions.assertArrayEquals(expectedDataOneStopInverted, TypeIICorrectionTask.getAlignedIndices(oneJumpEic,
   0, 10, monoisotopicEic, 5, 20));



   //Checking the method for an eic that contains two jumps in retention time in the overlap feature.
   RawDataFile twoStopsFile = new RawDataFileImpl("file", null, null);
   List<Scan> twoStopsScans = makeSomeScans(twoStopsFile, 3, 0.1f, 0.2f);
   twoStopsScans.addAll(makeSomeScans(twoStopsFile, 4, 0.1f, 0.9f));
   twoStopsScans.addAll(makeSomeScans(twoStopsFile, 4, 0.1f, 1.7f));
   IonTimeSeries<Scan> twoStopEic = new SimpleIonTimeSeries(null, makeFilledArray(11),
   new double[]{0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0}, twoStopsScans);

   int[][] expectedDataTwoStops = new int[][]{{2, 3, 10, 11, 18, 19, 20},
   {0, 1, 4, 5, 8, 9, 10}};

   Assertions.assertArrayEquals(expectedDataTwoStops,
   TypeIICorrectionTask.getAlignedIndices(monoisotopicEic, 2, 20,
   twoStopEic, 0, 10));


   //Checking the method for an eic that contains one jump in retention time in the overlap feature at the end of the overlapping range.
   RawDataFile stopAtEndFile = new RawDataFileImpl("file", null, null);
   List<Scan> stopAtEndScans = makeSomeScans(stopAtEndFile, 4, 0.1f, 1.4f);
   stopAtEndScans.addAll(makeSomeScans(stopAtEndFile, 7, 0.1f, 2.5f));
   IonTimeSeries<Scan> stopAtEndEic = new SimpleIonTimeSeries(null, makeFilledArray(11),
   new double[]{0, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1}, stopAtEndScans);

   int[][] expectedDataStopAtEnd = new int[][]{{14, 15, 16}, {0, 1, 2}};

   Assertions.assertArrayEquals(expectedDataStopAtEnd,
   TypeIICorrectionTask.getAlignedIndices(monoisotopicEic, 14, 17,
   stopAtEndEic, 0, 3));
   }
   */

}