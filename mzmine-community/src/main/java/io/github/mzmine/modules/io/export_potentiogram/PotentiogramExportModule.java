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

package io.github.mzmine.modules.io.export_potentiogram;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeriesUtils;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.util.ExitCode;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PotentiogramExportModule implements MZmineModule {

  //  Logger
  private static final Logger logger = Logger.getLogger(PotentiogramExportModule.class.getName());

  public static void exportPotentiogram(ModularFeatureListRow row) {

//    Get parameters from user.
    ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(PotentiogramExportModule.class);

    ExitCode exitCode = parameters.showSetupDialog(true);
    if (exitCode != ExitCode.OK) {
      return;
    }

    double delayTime = parameters.getValue(PotentiogramExportParameters.delayTime);
    double potentialRampSpeed = parameters.getValue(
        PotentiogramExportParameters.potentialRampSpeed);
    Range<Double> potentialRange = parameters.getValue(PotentiogramExportParameters.potentialRange);

//    Extract transient data from row.
    ModularFeature feature = row.getBestFeature();
    List<Scan> allFeatureScans = Arrays.asList(
        ScanSelection.MS1.getMatchingScans(feature.getRawDataFile()));
    IonTimeSeries<? extends Scan> eic = IonTimeSeriesUtils.remapRtAxis(feature.getFeatureData(),
        allFeatureScans);

//    Convert Rt to applied potential and extract scans in potential range.
    List<Double> intensities = new ArrayList<>();
    List<Double> potentials = new ArrayList<>();

    for (int i = 0; i < eic.getNumberOfValues(); i++) {

      double rt = eic.getRetentionTime(i);
      if ((rt * 60) < delayTime) {
        continue;
      }

      double potential = ((rt * 60) - delayTime) * potentialRampSpeed;
      if (potential < potentialRange.lowerEndpoint()) {
        continue;
      } else if (potential >= potentialRange.upperEndpoint()) {
        break;
      }

      potentials.add(potential);
      intensities.add(eic.getIntensity(i));
    }

//Write data to CSV file
    File file = parameters.getValue(PotentiogramExportParameters.path);

    try {
      FileWriter writer = new FileWriter(file);
      writer.append("Potential [mV], Intensity [a.u.]");
      writer.append("\n");

      for (int i = 0; i < potentials.size(); i++) {
        writer.append(String.valueOf(potentials.get(i)));
        writer.append(",");
        writer.append(String.valueOf(intensities.get(i)));
        writer.append("\n");
      }
      writer.flush();
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
      logger.log(Level.WARNING, e.getMessage(), e);
    }
  }

  @Override
  public @NotNull String getName() {
    return "Export Potentiogram";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return PotentiogramExportParameters.class;
  }
}
