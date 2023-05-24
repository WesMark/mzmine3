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

    featureLists = parameters.getValue(MeanParameters.featureList).getMatchingFeatureLists();
    numRows = Arrays.stream(featureLists).mapToInt(ModularFeatureList::getNumberOfRows).sum();
    exportEnabled = parameters.getValue(MeanParameters.export);

    if (exportEnabled) {
      SimpleParameterSet exportParameter = parameters.getParameter(MeanParameters.export)
          .getEmbeddedParameters();
      mz = exportParameter.getValue(MeanExportParameters.mz);
      mzTolerance = exportParameter.getValue(MeanExportParameters.mzTolerance);
      mzRange = Range.closed(mz - mzTolerance, mz + mzTolerance);
      filePath = exportParameter.getValue(MeanExportParameters.file).getAbsolutePath();

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

    XSSFWorkbook workbook = new XSSFWorkbook();
    XSSFSheet sheet = workbook.createSheet("RSD");
    Row headerRow = sheet.createRow(0);

    headerRow.createCell(0).setCellValue("File name");
    headerRow.createCell(1).setCellValue("feature m/z");
    headerRow.createCell(2).setCellValue("mean intensity");
    headerRow.createCell(3).setCellValue("rsd");

    DecimalFormat mzFormat = new DecimalFormat("0.0000");
    DecimalFormat meanIntensityFormat = new DecimalFormat("0.00");
    DecimalFormat rsdFormat = new DecimalFormat("0.000");

    for (int i = 0; i < featureLists.length; i++) {

      ModularFeatureList featureList = featureLists[i];

      List<Double> mzs = new ArrayList<>();
      List<Double> meanIntensities = new ArrayList<>();
      List<Double> rsds = new ArrayList<>();

      for (FeatureListRow row : featureList.getRows()) {

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

        //RSD
        double rsd = calculateRSD(intensities, meanIntensity, numScans);
        row.set(RsdType.class, rsd);

        //Adding the feature to the list for later export.
        if (exportEnabled && mzRange.contains(feature.getMZ())) {
          mzs.add(Double.parseDouble(mzFormat.format(feature.getMZ())));
          meanIntensities.add(Double.parseDouble(meanIntensityFormat.format(meanIntensity)));
          rsds.add(Double.parseDouble(rsdFormat.format(rsd)));
        }

        processed++;
      }

      if (exportEnabled) {

        int closestFeatureIndex = getClosestFeatureIndex(mzs);

        Row row = sheet.createRow(i + 1);

        row.createCell(0).setCellValue(featureList.getName());
        row.createCell(1).setCellValue(mzs.get(closestFeatureIndex));
        row.createCell(2).setCellValue(meanIntensities.get(closestFeatureIndex));
        row.createCell(3).setCellValue(rsds.get(closestFeatureIndex));
      }
    }

    if (exportEnabled) {

      for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
        sheet.autoSizeColumn(i);
      }

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

  private double calculateRSD(double[] intensities, double meanIntensity, int numScans) {

    double sumDeviation = 0;
    for (double intensity : intensities) {

      double deviation = Math.pow(intensity - meanIntensity, 2d);
      sumDeviation += deviation;
    }

    double sd = Math.sqrt(sumDeviation / numScans);
    return sd / meanIntensity;
  }

  private double calculateMeanIntensity(double[] intensities, int numScans) {

    double sumIntensity = Arrays.stream(intensities).sum();
    return sumIntensity / numScans;
  }

  private int getClosestFeatureIndex(List<Double> mzs) {

    if (mzs.size() == 1) {
      return 0;
    }

    double minDifference = Double.MAX_VALUE;
    int closestIndex = 0;

    for (int i = 0; i < mzs.size(); i++) {
      double difference = Math.abs(mzs.get(i) - mz);

      if (difference < minDifference) {
        closestIndex = i;
      }
    }

    return closestIndex;
  }
}
