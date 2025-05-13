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

package io.github.mzmine.modules.io.export_feature_average_int;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

public class AverageIntExportTask extends AbstractTask {

  public static final Logger logger = Logger.getLogger(AverageIntExportTask.class.getName());
  private FeatureList featureList;
  private File file;


  public AverageIntExportTask(ParameterSet parameters, @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate); // no new data stored -> null
    this.featureList = parameters.getValue(AverageIntExportParameters.featureList)
        .getMatchingFeatureLists()[0];
    this.file = parameters.getValue(AverageIntExportParameters.file);
  }


  @Override
  public void run() {

    setStatus(TaskStatus.PROCESSING);

    List<RawDataFile> rawDataFiles = featureList.getRawDataFiles();

    int numRows = featureList.getNumberOfRows();
    int numRawDataFiles = rawDataFiles.size();

    List<List<Double>> outputData = new ArrayList<>();

    for (int i = 0; i < numRows; i++) {

      FeatureListRow row = featureList.getRow(i);
      List<Double> rowAverageIntensities = new ArrayList<>();

      for (int j = 0; j < numRawDataFiles; j++) {

        RawDataFile rawDataFile = rawDataFiles.get(j);

        if (row.getFeature(rawDataFile) == null) {
          rowAverageIntensities.add((double) 0);
          continue;
        }

        Feature feature = row.getFeature(rawDataFile);

        int numDataPoints = feature.getFeatureData().getNumberOfValues();
        double sumIntensity = 0;

        for (int k = 0; k < numDataPoints; k++) {

          sumIntensity = sumIntensity + feature.getFeatureData().getIntensity(k);
        }

        double averageIntensity = sumIntensity / numDataPoints;
        rowAverageIntensities.add(averageIntensity);
      }

      outputData.add(rowAverageIntensities);
    }

    try (FileWriter fileWriter = new FileWriter(file)) {

      fileWriter.append("m/z");

      for (RawDataFile rawDataFile : rawDataFiles) {
        fileWriter.append(",");
        fileWriter.append(rawDataFile.getFileName());
      }

      for (int i = 0; i < outputData.size(); i++) {

        fileWriter.append("\n");
        fileWriter.append(String.valueOf(featureList.getRow(i).getAverageMZ()));

        for (int j = 0; j < numRawDataFiles; j++) {
          fileWriter.append(",");
          fileWriter.append(String.valueOf(outputData.get(i).get(j)));
        }
      }

      fileWriter.flush();


    } catch (IOException e) {
      e.printStackTrace();
      logger.log(Level.WARNING, e.getMessage());
    }

    setStatus(TaskStatus.FINISHED);
  }


  @Override
  public String getTaskDescription() {
    return "Exporting features average intensities";
  }

  @Override
  public double getFinishedPercentage() {
    return 0;
  }
}
