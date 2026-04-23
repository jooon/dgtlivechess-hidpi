package com.sun.javafx.scene.control.skin;

import com.sun.javafx.scene.control.behavior.BehaviorBase;
import javafx.scene.control.Control;
import javafx.scene.control.SkinBase;

public class BehaviorSkinBase<C extends Control, B extends BehaviorBase<C>> extends SkinBase<C> {
  public BehaviorSkinBase(C control, B behavior) {
    super(control);
  }
}
