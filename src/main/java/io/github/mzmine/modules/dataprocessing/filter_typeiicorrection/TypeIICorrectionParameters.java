/*
 * Copyright 2006-2022 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package io.github.mzmine.modules.dataprocessing.filter_typeiicorrection;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import java.text.DecimalFormat;

public class TypeIICorrectionParameters extends SimpleParameterSet {

  public static final FeatureListsParameter featureLists = new FeatureListsParameter();

  public static final OriginalFeatureListHandlingParameter handleOriginal = new OriginalFeatureListHandlingParameter(
      false);

  public static final DoubleParameter mergeWidth = new DoubleParameter("Merge Width",
      "The resolution of the calculated isotope pattern.", new DecimalFormat("0.000"), 0.005);

  public static final IntegerParameter areaCutoff = new IntegerParameter("Area Cutoff",
      "The area cutoff for the corrected features. Features with corrected areas below this threshold will be removed.",
      100);

  public static final MZToleranceParameter mzTolerance = new MZToleranceParameter("m/z-Tolerance",
      "The tolerance between the measured m/z-value and the isotopes calculated m/z-value", 0.005,
      5);

  public TypeIICorrectionParameters() {
    super(new Parameter[]{featureLists, handleOriginal, mergeWidth, mzTolerance, areaCutoff});
  }

}
