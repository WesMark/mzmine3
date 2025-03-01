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

package io.github.mzmine.util.javafx;

import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.util.FeatureSorter;
import io.github.mzmine.util.SortingDirection;
import io.github.mzmine.util.SortingProperty;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

public class SortableFeatureComboBox extends FlowPane {

  private final ComboBox<Feature> data;

  public SortableFeatureComboBox() {
    super();
    setVgap(5);

    data = new ComboBox<>();
    final ComboBox<SortingProperty> sortBox = new ComboBox<>(
        FXCollections.observableArrayList(SortingProperty.values()));

    sortBox.valueProperty().addListener(((observable, oldValue, newValue) -> {
      if(newValue == null) {
        return;
      }
      final List<Feature> arrayList = new ArrayList<>(data.getItems());
      final FeatureSorter sorter = getSorter(sortBox.getValue());
      arrayList.sort(sorter);
      data.setItems(FXCollections.observableArrayList(arrayList));
    }));

    final FlowPane sortPane = new FlowPane(new Label("Sort by: "), sortBox);
    setHgap(5);
    getChildren().addAll(data, sortPane);
  }

  private FeatureSorter getSorter(SortingProperty property) {
    return new FeatureSorter(property, SortingDirection.Ascending);
  }

  public ComboBox<Feature> getFeatureBox() {
    return data;
  }
}
