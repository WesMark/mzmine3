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

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.example.LearnerParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
import java.time.Instant;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * A Module creates tasks which are then added queue
 */
public class TypeIICorrectionModule implements MZmineProcessingModule {
  // #################################################################
  // IMPORTANT
  // add your module to the {@link MainMenu.fxml}
  // do not forget to put your module in the BatchModeModulesList
  // in the package: io.github.mzmine.main
  // #################################################################

  private static final String MODULE_NAME = "Type-II-Correction";
  private static final String MODULE_DESCRIPTION = "describe";

  // this method is called after user selects the parameters and clicks okay (or in batch mode)
  @Override
  @NotNull
  public ExitCode runModule(@NotNull MZmineProject project, @NotNull ParameterSet parameters,
      @NotNull Collection<Task> tasks, @NotNull Instant moduleCallDate) {

    // get parameters here only needed to run one task per featureList
    FeatureList[] featureLists = parameters.getParameter(LearnerParameters.featureLists).getValue()
        .getMatchingFeatureLists();

    // create and start one task for each feature list
    for (final FeatureList featureList : featureLists) {
      Task newTask = new TypeIICorrectionTask(project, featureList, parameters, null,
          moduleCallDate);
      // task is added to TaskManager that schedules later
      tasks.add(newTask);
    }

    return ExitCode.OK;
  }

  @Override
  public @NotNull MZmineModuleCategory getModuleCategory() {
    /*
     * Change category - used in the batch list module to group processing steps
     */
    return MZmineModuleCategory.FEATURELISTFILTERING;
  }

  @Override
  public @NotNull Class<? extends ParameterSet> getParameterSetClass() {
    return TypeIICorrectionParameters.class;
  }

  @Override
  public @NotNull String getName() {
    return MODULE_NAME;
  }

  @Override
  public @NotNull String getDescription() {
    return MODULE_DESCRIPTION;
  }
}
