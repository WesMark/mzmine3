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
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;

public class TypeIICorrectionParameters extends SimpleParameterSet {

  /*
   * Define any parameters here (see io.github.mzmine.parameters for parameter types)
   * static is needed here to use this parameter as a key to lookup values
   */
  public static final FeatureListsParameter featureLists = new FeatureListsParameter();

  public static final OriginalFeatureListHandlingParameter handleOriginal = new OriginalFeatureListHandlingParameter(
      false);

  public TypeIICorrectionParameters() {
    /*
     * The order of the parameters is used to construct the parameter dialog automatically
     */
    super(new Parameter[]{featureLists});
  }

}
