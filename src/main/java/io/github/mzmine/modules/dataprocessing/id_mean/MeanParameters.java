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

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.submodules.OptionalModuleParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import java.text.DecimalFormat;

public class MeanParameters extends SimpleParameterSet {

  public static final FeatureListsParameter featureList = new FeatureListsParameter();


  public static final OptionalModuleParameter<MeanExportParameters> export = new OptionalModuleParameter<>(
      "Export", "Export the mean intensity and the rsd", new MeanExportParameters());

  public MeanParameters() {
    super(new Parameter[]{featureList});
  }

}

class MeanExportParameters extends SimpleParameterSet {

  public static final DoubleParameter mz = new DoubleParameter("m/z",
      "The features m/z value that will be exported.", new DecimalFormat("0.0000"));

  public static final MZToleranceParameter mzTolerance = new MZToleranceParameter("m/z tolerance",
      "The tolerance for the features around the entered m/z-value");

  public static final FileNameParameter file = new FileNameParameter("File Path",
      "The file pathe the values will be exported to", FileSelectionType.SAVE);

}
