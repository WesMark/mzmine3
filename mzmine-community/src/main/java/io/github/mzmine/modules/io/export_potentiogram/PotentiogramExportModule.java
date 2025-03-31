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
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.util.ExitCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PotentiogramExportModule implements MZmineModule {

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
