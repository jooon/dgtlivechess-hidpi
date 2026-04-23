package com.novotea.chess.javafx.ui;

import com.sun.javafx.scene.control.behavior.CellBehaviorBase;
import javafx.scene.control.FocusModel;
import javafx.scene.control.MultipleSelectionModel;

public class ItemCellBehaviour<T> extends CellBehaviorBase<ItemCell<T>> {
  public ItemCellBehaviour(ItemCell<T> control) {
    super(control);
  }

  @Override
  protected FocusModel<?> getFocusModel() {
    return getCellContainer().getFocusModel();
  }

  @Override
  protected ItemView<T> getCellContainer() {
    return getNode().getItemView();
  }

  @Override
  protected MultipleSelectionModel<?> getSelectionModel() {
    return null;
  }

  @Override
  protected void edit(ItemCell<T> control) {}
}
