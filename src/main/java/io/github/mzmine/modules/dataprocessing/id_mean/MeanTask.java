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

import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.numbers.MeanIntensityType;
import io.github.mzmine.datamodel.features.types.numbers.RsdType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.time.Instant;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public class MeanTask extends AbstractTask {

  private final ModularFeatureList[] featureLists;
  private final int numRows;
  private int processed;

  protected MeanTask(@NotNull Instant moduleCallDate, ParameterSet parameters) {
    super(null, moduleCallDate);

    featureLists = parameters.getValue(MeanParameters.featureList).getMatchingFeatureLists();
    numRows = Arrays.stream(featureLists).mapToInt(ModularFeatureList::getNumberOfRows).sum();
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

    for (ModularFeatureList featureList : featureLists) {

      for (FeatureListRow row : featureList.getRows()) {

        Feature feature = row.getBestFeature();

        if (feature == null || feature.getFeatureStatus() == FeatureStatus.UNKNOWN) {
          processed++;
          continue;
        }

        //Number of scans in the feature.
        int numScans = feature.getNumberOfDataPoints();

        //Sum of all the feature scans intensities.
        double[] intensities = new double[numScans];
        feature.getFeatureData().getIntensityValues(intensities);
        double sumIntensity = Arrays.stream(intensities).sum();

        //Features mean intensity.
        double meanIntensity = sumIntensity / numScans;

        row.set(MeanIntensityType.class, meanIntensity);

        //RSD
        double sumDeviation = 0;
        for (double intensity : intensities) {

          double deviation = Math.pow(intensity - meanIntensity, 2d);
          sumDeviation += deviation;
        }

        double sd = Math.sqrt(sumDeviation / numScans);
        double rsd = sd / meanIntensity;

        row.set(RsdType.class, rsd);
      }

      processed++;
    }

    setStatus(TaskStatus.FINISHED);
  }
}
