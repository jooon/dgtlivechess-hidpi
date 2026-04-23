package com.sun.javafx.scene.control.skin;

import com.sun.javafx.scene.control.behavior.BehaviorBase;
import javafx.scene.control.Cell;

public class CellSkinBase<C extends Cell, B extends BehaviorBase<C>>
    extends javafx.scene.control.skin.CellSkinBase<C> {
  public CellSkinBase(C control, B behavior) {
    super(control);
  }
}
