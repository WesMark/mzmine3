/*
 * Copyright (c) 2004-2022 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.id_mean;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.numbers.MeanIntensityType;
import io.github.mzmine.datamodel.features.types.numbers.RsdType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;

public class MeanTask extends AbstractTask {

  //parameter
  private final ModularFeatureList[] featureLists;
  private final double mz;
  private final double mzTolerance;
  private final Range<Double> mzRange;
  private final String filePath;
  private final boolean exportEnabled;

  //finished percentage.
  private final int numRows;
  private int processed;

  protected MeanTask(@NotNull Instant moduleCallDate, ParameterSet parameters) {
    super(null, moduleCallDate);

    //Set parameters.
    featureLists = parameters.getValue(MeanParameters.featureList).getMatchingFeatureLists();
    numRows = Arrays.stream(featureLists).mapToInt(ModularFeatureList::getNumberOfRows).sum();
    exportEnabled = parameters.getValue(MeanParameters.export);

    //Set export parameters.
    if (exportEnabled) {
      SimpleParameterSet exportParameter = parameters.getParameter(MeanParameters.export)
          .getEmbeddedParameters();
      mz = exportParameter.getValue(MeanExportParameters.mz);
      mzTolerance = exportParameter.getValue(MeanExportParameters.mzTolerance);
      mzRange = Range.closed(mz - mzTolerance, mz + mzTolerance);
      filePath = exportParameter.getValue(MeanExportParameters.file).getAbsolutePath();

      //Set export parameters to null/0 if export is disabled.
    } else {
      mz = 0;
      mzTolerance = 0;
      mzRange = null;
      filePath = null;
    }
  }

  @Override
  public String getTaskDescription() {
    return "Calculates the mean signal intensity for all detected features.";
  }

  @Override
  public double getFinishedPercentage() {
    return processed / (double) numRows;
  }

  @Override
  public void run() {

    setStatus(TaskStatus.PROCESSING);

    //Creating an Excel workbook.
    XSSFWorkbook workbook = new XSSFWorkbook();
    XSSFSheet sheet = workbook.createSheet("RSD");

    //Creating a header row in the Excel workbook.
    Row headerRow = sheet.createRow(0);
    headerRow.createCell(0).setCellValue("File name");
    headerRow.createCell(1).setCellValue("feature m/z");
    headerRow.createCell(2).setCellValue("mean intensity");
    headerRow.createCell(3).setCellValue("rsd");

    //Initializing decimal formats for the data to be exported.
    DecimalFormat mzFormat = new DecimalFormat("0.0000");
    DecimalFormat meanIntensityFormat = new DecimalFormat("0.00");
    DecimalFormat rsdFormat = new DecimalFormat("0.000");

    //Going over all selected feature lists.
    for (int i = 0; i < featureLists.length; i++) {

      ModularFeatureList featureList = featureLists[i];

      //Creating lists to add the data to be exported.
      List<Double> mzs = new ArrayList<>();
      List<Double> meanIntensities = new ArrayList<>();
      List<Double> rsds = new ArrayList<>();

      //Going over all rows in the feature list.
      for (FeatureListRow row : featureList.getRows()) {

        //Continuing with the next feature if the feature is null.
        Feature feature = row.getBestFeature();
        if (feature == null || feature.getFeatureStatus() == FeatureStatus.UNKNOWN) {
          processed++;
          continue;
        }

        //Number of scans in the feature.
        int numScans = feature.getNumberOfDataPoints();

        //Features intensities.
        double[] intensities = new double[numScans];
        feature.getFeatureData().getIntensityValues(intensities);

        //Mean intensity.
        double meanIntensity = calculateMeanIntensity(intensities, numScans);
        row.set(MeanIntensityType.class, meanIntensity);

        //RSD.
        double rsd = calculateRSD(intensities, meanIntensity, numScans);
        row.set(RsdType.class, rsd);

        //Adding the feature to the list for later export if export is enabled.
        if (exportEnabled && mzRange.contains(feature.getMZ())) {
          mzs.add(Double.parseDouble(mzFormat.format(feature.getMZ())));
          meanIntensities.add(Double.parseDouble(meanIntensityFormat.format(meanIntensity)));
          rsds.add(Double.parseDouble(rsdFormat.format(rsd)));
        }

        processed++;
      }

      //Adding the data of the feature within the mz range with the closest mz value.
      if (exportEnabled) {

        int closestFeatureIndex = getClosestFeatureIndex(mzs);

        Row row = sheet.createRow(i + 1);
        row.createCell(0).setCellValue(featureList.getName());

        if (closestFeatureIndex != -1) {
          row.createCell(1).setCellValue(mzs.get(closestFeatureIndex));
          row.createCell(2).setCellValue(meanIntensities.get(closestFeatureIndex));
          row.createCell(3).setCellValue(rsds.get(closestFeatureIndex));
        }
      }
    }

    if (exportEnabled) {

      //Resizing the Excel sheets columns.
      for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
        sheet.autoSizeColumn(i);
      }

      //Writing the Excel file to disk.
      try {
        File file = new File(filePath + ".xlsx");
        FileOutputStream outputStream = new FileOutputStream(file);
        workbook.write(outputStream);
        outputStream.close();

      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    setStatus(TaskStatus.FINISHED);
  }

  /**
   * Calculates the relative standard deviation from an array of values.
   *
   * @param values
   * @param meanValue
   * @param numValues
   * @return The relative standard deviation.
   */
  private double calculateRSD(double[] values, double meanValue, int numValues) {

    double sumDeviation = 0;
    for (double intensity : values) {

      double deviation = Math.pow(intensity - meanValue, 2d);
      sumDeviation += deviation;
    }

    double sd = Math.sqrt(sumDeviation / numValues);
    return sd / meanValue;
  }

  /**
   * Calculates the mean from an array of values.
   *
   * @param values
   * @param numValues
   * @return The mean value.
   */
  private double calculateMeanIntensity(double[] values, int numValues) {

    double sumIntensity = Arrays.stream(values).sum();
    return sumIntensity / numValues;
  }

  /**
   * Returns the closest features index to the given mz value. Returns -1 if the list is empty.
   *
   * @param mzs
   * @return
   */
  private int getClosestFeatureIndex(List<Double> mzs) {

    //Return -1 if the list is empty.
    if (mzs.isEmpty()) {
      return -1;

      //Return the first value if there is only one value in the list.
    } else if (mzs.size() == 1) {
      return 0;
    }

    double minDifference = Double.MAX_VALUE;
    int closestIndex = 0;

    //Going through the list and looking for the minimal delta mz.
    for (int i = 0; i < mzs.size(); i++) {
      double difference = Math.abs(mzs.get(i) - mz);

      if (difference < minDifference) {
        closestIndex = i;
      }
    }

    return closestIndex;
  }
}
