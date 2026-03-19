

/*
 * TableViewSupport.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.window;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import jloda.fx.util.BasicFX;

import java.util.List;

public class TableViewSupport {

	public static void apply(TableView<TreeRow> treeTableView,
							 TableColumn<TreeRow, String> treeColumn, TableColumn<TreeRow, Boolean> runColumn,
							 TableColumn<TreeRow, Boolean> showColumn, BooleanProperty disableAllShow,
							 MainWindowController controller) {
		treeTableView.setEditable(true);
		treeTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		// Tree name
		treeColumn.setCellValueFactory(cd -> cd.getValue().nameProperty());

		// Use checkbox
		runColumn.setEditable(true);
		runColumn.setCellValueFactory(cd -> cd.getValue().runProperty());
		runColumn.setCellFactory(col -> {
			var cell = new CheckBoxTableCell<TreeRow, Boolean>();
			cell.setEditable(true);
			return cell;
		});

		// Show checkbox
		showColumn.setEditable(true);
		showColumn.setCellValueFactory(cd -> cd.getValue().showProperty());
		showColumn.setCellFactory(col -> {
			var cell = new CheckBoxTableCell<TreeRow, Boolean>();
			cell.setEditable(true);
			cell.disableProperty().bind(disableAllShow);
			return cell;
		});

		treeTableView.setRowFactory(tv -> {
			TableRow<TreeRow> row = new TableRow<>();

			var menu = new ContextMenu();
			menu.getItems().addAll(BasicFX.copyMenu(List.of(controller.getUseAllMenuItem(), controller.getUseNoneMenuItem(), new SeparatorMenuItem(), controller.getShowAllMenuItem(), controller.getShowNoneMenuItem()), false));

			// Only show menu for non-empty rows:
			row.contextMenuProperty().bind(
					Bindings.when(row.emptyProperty())
							.then((ContextMenu) null)
							.otherwise(menu)
			);

			// Ensure right-click selects the row (without nuking multi-selection unless desired)
			row.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
				if (evt.getButton() == MouseButton.SECONDARY && !row.isEmpty()) {
					MultipleSelectionModel<TreeRow> sm = tv.getSelectionModel();

					int index = row.getIndex();
					if (!sm.getSelectedIndices().contains(index)) {
						// common behavior: right-click focuses/selects only that row
						// If you'd rather ADD to selection, use sm.select(index) instead.
						sm.clearAndSelect(index);
					}
				}
			});

			return row;
		});
	}
}