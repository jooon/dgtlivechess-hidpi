package com.novotea.chess.javafx.ui;

import com.sun.javafx.scene.control.behavior.BehaviorBase;
import com.sun.javafx.scene.control.inputmap.InputMap;

public class ItemViewBehaviour<T> extends BehaviorBase<ItemView<T>> {
  private final InputMap<ItemView<T>> inputMap;

  public ItemViewBehaviour(ItemView<T> control) {
    super(control);
    inputMap = createInputMap();
  }

  @Override
  public InputMap<ItemView<T>> getInputMap() {
    return inputMap;
  }
}
