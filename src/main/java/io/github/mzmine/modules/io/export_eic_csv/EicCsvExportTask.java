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

package io.github.mzmine.modules.io.export_eic_csv;

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeriesUtils;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EicCsvExportTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(EicCsvExportTask.class.getName());

  //Parameter
  private final ModularFeatureList featureList;
  private final String selectedIDs;
  private final File file;

  //Task Progress
  private int numRawDataFiles;
  private int numProcessedRawDataFiles;


  protected EicCsvExportTask(@Nullable ParameterSet parameter, @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate);
    file = parameter.getParameter(EicCsvExportParameters.path).getValue();
    featureList = parameter.getParameter(EicCsvExportParameters.featureList).getValue()
        .getMatchingFeatureLists()[0];
    selectedIDs = parameter.getParameter(EicCsvExportParameters.featureIDs).getValue();
  }

  @Override
  public void run() {

    setStatus(TaskStatus.PROCESSING);

    //Task progress variables
    numRawDataFiles = featureList.getRawDataFiles().size();
    numProcessedRawDataFiles = 0;

    //Create directory for output files
    String filePath = file.getAbsolutePath();
    try {
      Files.createDirectory(Paths.get(file.getAbsolutePath()));
    } catch (IOException e) {
      e.printStackTrace();
      logger.log(Level.WARNING, e.getMessage());
    }

    //Split user input IDs to List
    List<String> IDs = Arrays.asList(selectedIDs.split("\\s*,\\s*"));

    for (int j = 0; j < numRawDataFiles; j++) {

      //Lists for export data to be stored in
      List<ModularFeature> features = new ArrayList<>();
      List<IonTimeSeries<Scan>> eics = new ArrayList<>();

      //Map linking feature ID to their index in the EIc list
      Map<String, Integer> idIndexMap = new HashMap<>();

      //List of all features in selected feature list
      List<ModularFeature> allFeatures = featureList.getFeatures(featureList.getRawDataFile(j));

      //Extracting features with selected IDs from all features
      for (String ID : IDs) {

        int featureID = Integer.parseInt(ID);

        for (ModularFeature feature : allFeatures) {
          if (feature.getRow().getID() == featureID) {
            features.add(feature);

            //Linking feature ID to list index
            idIndexMap.put(ID, features.indexOf(feature));
            break;
          }
        }
      }

      //Scan selection for MS1 scans
      ScanSelection scanSelection = new ScanSelection(1);

      //Extracting EICs from selected features
      for (Feature feature : features) {

        List<Scan> scansMs1 = Arrays.asList(
            scanSelection.getMatchingScans(feature.getRawDataFile()));

        IonTimeSeries<Scan> eic = IonTimeSeriesUtils.remapRtAxis(feature.getFeatureData(),
            scansMs1);

        eics.add(eic);
      }

      //Number of scans for formatting csv file.
      List<Scan> ms1Scans = Arrays.asList(
          scanSelection.getMatchingScans(featureList.getRawDataFile(j)));
      int numScans = scanSelection.getMatchingScans(featureList.getRawDataFile(j)).length;

      //Debug logger
      logger.log(Level.INFO, "Length of Raw Data File: " + String.valueOf(numScans));
      logger.log(Level.INFO, "Length of EIC List: " + String.valueOf(eics.size()));
      logger.log(Level.INFO, "Length of Export Feature List: " + String.valueOf(features.size()));
      logger.log(Level.INFO, "Size of Map: " + String.valueOf(idIndexMap.size()));
      for (String id : IDs) {
        logger.log(Level.INFO, "ID: " + id + ", ExportIndex: " + idIndexMap.get(id));
      }

      try {

        //Writing file
        String rawDataFileName = featureList.getRawDataFile(j).getFileName();
        FileWriter writer = new FileWriter(filePath + File.separator + rawDataFileName + ".csv");

        //CSV file header
        writer.write("rt");
        writer.append(";");
        for (String id : IDs) {
          writer.write(id);
          writer.append(";");
        }
        writer.append("\n");

        //Writing EIC data
        for (int i = 0; i < numScans; i++) {

          //RT for row
          float rt = ms1Scans.get(i).getRetentionTime();
          writer.write(String.valueOf(rt));
          writer.append(";");

          //Intensities for features
          for (String ID : IDs) {

            Integer featureExportListIndex = idIndexMap.get(ID);

            //Writing 0 if feature is not present in raw data file
            if (featureExportListIndex == null) {
              writer.write("0.0");
              writer.append(";");
            }
            //Writing intensity value otherwise
            else {
              double intensity = eics.get(featureExportListIndex).getIntensity(i);
              writer.write(String.valueOf(intensity));
              writer.append(";");
            }
          }
          writer.append("\n");
        }

        writer.flush();
        writer.close();
      }

      //IOException des FileWriter.
      catch (IOException e) {
        e.printStackTrace();
        logger.log(Level.WARNING, e.getMessage());
      }

      //Task progress
      numProcessedRawDataFiles++;
    }

    setStatus(TaskStatus.FINISHED);
  }

  @Override
  public String getTaskDescription() {
    return "Exporting the selected eic's";
  }

  @Override
  public double getFinishedPercentage() {
    if (numRawDataFiles > 0) {
      return (double) numProcessedRawDataFiles / (double) numRawDataFiles;
    } else {
      return 0;
    }
  }
}
