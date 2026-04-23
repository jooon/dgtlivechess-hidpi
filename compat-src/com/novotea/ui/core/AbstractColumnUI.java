package com.novotea.ui.core;

import com.novotea.ui.core.layout.Layout;
import com.novotea.ui.core.layout.UISetting;
import java.lang.reflect.Method;
import java.util.Comparator;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

public abstract class AbstractColumnUI<S, T> implements ColumnUI<S, T> {
  private Pos align;
  private boolean editable;

  protected abstract UISetting settings(Layout layout);

  protected void setup(UISetting setting) {}

  protected Pos align() {
    return align;
  }

  @Override
  public ColumnUI<S, T> align(Pos align) {
    this.align = align;
    return this;
  }

  @Override
  public AbstractColumnUI<S, T> editable() {
    editable = true;
    return this;
  }

  @Override
  public boolean isEditable() {
    return editable;
  }

  @Override
  public AbstractColumnUI<S, T> sortable() {
    ui().setSortable(true);
    return this;
  }

  @Override
  public ColumnUI<S, T> sortable(Comparator<T> comparator) {
    ui().setComparator(comparator);
    return null;
  }

  @Override
  public final TableColumn<S, T> ui(Layout layout) {
    UISetting setting = settings(layout);
    TableColumn<S, T> column = ui();
    setup(setting);
    column.setEditable(editable);
    disableReordering(column);

    StringProperty text = column.textProperty();
    text.bind(setting.label());

    DoubleProperty minWidth = column.minWidthProperty();
    minWidth.bind(setting.columnMinWidth());

    DoubleProperty prefWidth = column.prefWidthProperty();
    prefWidth.bind(setting.columnPrefWidth());

    DoubleProperty maxWidth = column.maxWidthProperty();
    maxWidth.bind(setting.columnMaxWidth());

    column.setCellFactory(ignored -> buildCell(layout, setting));
    return column;
  }

  private TableCell<S, T> buildCell(Layout layout, UISetting setting) {
    TableCell<S, T> cell = cell().build(layout, setting);
    cell.setEditable(editable);
    if (align != null) {
      cell.setAlignment(align);
    }
    setting.handle(cell::addEventHandler);
    return cell;
  }

  private static void disableReordering(TableColumn<?, ?> column) {
    try {
      Method setReorderable = TableColumn.class.getMethod("setReorderable", boolean.class);
      setReorderable.invoke(column, false);
    } catch (NoSuchMethodException ignored) {
      // JavaFX version does not expose reorderability on TableColumn.
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
