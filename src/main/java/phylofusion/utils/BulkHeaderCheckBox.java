/*
 * BulkHeaderCheckBox.java Copyright (C) 2026 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package phylofusion.utils;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class BulkHeaderCheckBox<T> {
	private final TableView<T> table;
	private final Function<T, BooleanProperty> propertyOfRow;
	private final CheckBox header = new CheckBox();

	private final InvalidationListener rowPropListener = obs -> refresh();
	private final List<BooleanProperty> observedProps = new ArrayList<>();

	private final ListChangeListener<T> selectionListener = c -> refresh();
	private final ListChangeListener<T> itemsListener = c -> refresh();

	public BulkHeaderCheckBox(TableView<T> table, Function<T, BooleanProperty> propertyOfRow) {
		this.table = table;
		this.propertyOfRow = propertyOfRow;

		header.setFocusTraversable(false);

		// Apply to observed rows on click
		header.setOnAction(e -> apply());

		// Refresh when selection or items change
		table.getSelectionModel().getSelectedItems().addListener(selectionListener);
		table.getItems().addListener(itemsListener);

		refresh();
	}

	public CheckBox getNode() {
		return header;
	}

	private ObservableList<T> observedRows() {
		var selected = table.getSelectionModel().getSelectedItems();
		return (selected != null && !selected.isEmpty()) ? selected : table.getItems();
	}

	private void detachObserved() {
		for (var p : observedProps) {
			p.removeListener(rowPropListener);
		}
		observedProps.clear();
	}

	private void attachObserved(ObservableList<T> rows) {
		for (var r : rows) {
			var p = propertyOfRow.apply(r);
			observedProps.add(p);
			p.addListener(rowPropListener);
		}
	}

	private boolean allTrue(ObservableList<T> rows) {
		for (var r : rows) {
			if (!propertyOfRow.apply(r).get()) return false;
		}
		return true;
	}

	private void refresh() {
		var rows = observedRows();

		detachObserved();

		if (rows == null || rows.isEmpty()) {
			header.setDisable(true);
			header.setSelected(false);
			return;
		}

		attachObserved(rows);

		header.setDisable(false);
		header.setSelected(allTrue(rows));
	}

	private void apply() {
		var rows = observedRows();
		if (rows == null || rows.isEmpty()) return;

		boolean currentlyAllTrue = allTrue(rows);
		boolean target = !currentlyAllTrue;

		for (var r : rows) {
			propertyOfRow.apply(r).set(target);
		}
		refresh();
	}
}