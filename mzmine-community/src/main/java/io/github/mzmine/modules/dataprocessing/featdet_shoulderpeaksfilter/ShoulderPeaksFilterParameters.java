/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.modules.dataprocessing.featdet_shoulderpeaksfilter;

import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.util.ExitCode;

public class ShoulderPeaksFilterParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter dataFiles = new RawDataFilesParameter();

  public static final DoubleParameter resolution = new DoubleParameter("Mass resolution",
      "Mass resolution is the dimensionless ratio of the mass of the peak divided by its width."
          + "\nPeak width is taken as the full width at half maximum intensity (FWHM).");

  public static final ComboParameter<PeakModelType> peakModel = new ComboParameter<PeakModelType>(
      "Peak model function", "Peaks under the curve of this peak model will be removed",
      PeakModelType.values());

  public ShoulderPeaksFilterParameters() {
    super(
        "https://mzmine.github.io/mzmine_documentation/module_docs/featdet_mass_detection/FTMS-shoulder-peak-filter.html",
        dataFiles, resolution, peakModel);
  }

  @Override
  public ExitCode showSetupDialog(boolean valueCheckRequired) {
    ShoulderPeaksFilterSetupDialog dialog = new ShoulderPeaksFilterSetupDialog(valueCheckRequired,
        this);
    dialog.showAndWait();
    return dialog.getExitCode();
  }

}
