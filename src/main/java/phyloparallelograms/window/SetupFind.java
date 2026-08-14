/*
 * SetupFind.java Copyright (C) 2026 Daniel H. Huson
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

package phyloparallelograms.window;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.SelectionMode;
import jloda.fx.find.FindToolBar;
import jloda.fx.find.Searcher;
import jloda.fx.find.TableViewSearcher;

class SetupFind {
	public static void apply(MainWindow window) {
		var controller = window.getController();

		var tableSearcher = new TableViewSearcher<>(controller.getTreeTable());
		tableSearcher.setName("Trees");
		var taxonSelectionModel = window.getTaxaSelectionModel();

		var document = window.getDocument();
		var taxaBlock = document.getTaxaBlock();

		var taxonSearcher = new Searcher<>(taxaBlock.getTaxa(),
				t -> taxonSelectionModel.isSelected(taxaBlock.get(t + 1)),
				(t, s) -> taxonSelectionModel.setSelected(taxaBlock.get(t + 1), s),
				new SimpleObjectProperty<>(SelectionMode.MULTIPLE),
				t -> taxaBlock.getLabel(t + 1), s -> s, (t, s) -> {
		}, () -> {
		}, () -> {
		});
		taxonSearcher.setSelectionFindable(false);
		taxonSearcher.setName("Taxa");

		var findToolBar = new FindToolBar(window.getStage(), tableSearcher, taxonSearcher);

		findToolBar.showFindToolBarProperty().bindBidirectional(controller.getFindCheckMenuItem().selectedProperty());
		controller.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		controller.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());

		// place the find tool bar below the tool bar (and its separator), just above the drawing, rather than above it
		controller.getRightVBox().getChildren().add(findToolBar);
	}
}
