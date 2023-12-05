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

import io.github.msdk.util.MsSpectrumUtil;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeriesUtils;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.scans.ScanUtils;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EicCsvExportTask extends AbstractTask {

  // Logger.
  private static final Logger logger = Logger.getLogger(EicCsvExportTask.class.getName());

  //Parameters.
  private final File file;
  private final List<Feature> features;


  protected EicCsvExportTask(@Nullable ParameterSet parameter, @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate);

    file = parameter.getParameter(EicCsvExportParameters.path).getValue();
    features = parameter.getParameter(EicCsvExportParameters.features).getValue();
  }

  @Override
  public void run() {

    setStatus(TaskStatus.PROCESSING);

    List<IonTimeSeries<Scan>> eics = new ArrayList<>();

    ScanSelection scanSelection = new ScanSelection(1);

    for (Feature feature : features) {

      List<Scan> scansMs1 = Arrays.asList(scanSelection.getMatchingScans(feature.getRawDataFile()));

      IonTimeSeries<Scan> eic = IonTimeSeriesUtils.remapRtAxis(feature.getFeatureData(), scansMs1);
      eics.add(eic);
    }

    int numScans = scanSelection.getMatchingScans(features.get(0).getRawDataFile()).length;

    try {
      FileWriter writer = new FileWriter(file);

      writer.write("rt");
      writer.append(";");

      for (Feature feature : features) {

        writer.write(String.valueOf(feature.getMZ()));
        writer.append(";");
      }

      writer.append("\n");

      for (int i = 0; i < numScans; i++) {

        float rt = eics.get(0).getRetentionTime(i);
        writer.write(String.valueOf(rt));
        writer.append(";");

        for (IonTimeSeries<Scan> eic : eics) {

          double intensity = eic.getIntensity(i);
          writer.write(String.valueOf(intensity));
          writer.append(";");
        }

        writer.append("\n");
      }

      writer.flush();
    }

    //IOException des FileWriter.
    catch (IOException e) {
      e.printStackTrace();
      logger.log(Level.WARNING, e.getMessage());
    }

    setStatus(TaskStatus.FINISHED);
  }

  @Override
  public String getTaskDescription() {
    return "Exporting the selected eic's";
  }

  @Override
  public double getFinishedPercentage() {
    return 0;
  }
}
