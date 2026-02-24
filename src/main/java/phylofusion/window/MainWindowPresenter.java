

/*
 * MainWindowPresenter.java Copyright (C) 2026 Daniel H. Huson
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
import javafx.collections.ListChangeListener;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import jloda.fx.util.FileOpenManager;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.SplashScreen;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import phylofusion.algorithm.PhyloFusionService;
import phylofusion.io.SaveBeforeClosingDialog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;

public class MainWindowPresenter {
	private final MainWindow mainWindow;

	public MainWindowPresenter(MainWindow window) {
		this.mainWindow = window;
		var controller = window.getController();
		var document = window.getDocument();


		controller.getTreeTable().getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		controller.getAboutMenuItem().setOnAction(e -> SplashScreen.showSplash(Duration.ofSeconds(30)));

		controller.getNewMenuItem().setOnAction(e -> NewWindow.apply());
		controller.getOpenMenuItem().setOnAction(FileOpenManager.createOpenFileEventHandler(window.getStage()));

		document.getTrees().addListener((ListChangeListener<PhyloTree>) e -> {
			while (e.next()) {
				if (e.wasAdded()) {
					for (var tree : e.getAddedSubList()) {
						var item = new TreeRow(tree.getName(), true, false, tree);
						controller.getTreeTable().getItems().add(item);
					}
				}
				if (e.wasRemoved()) {
					controller.getTreeTable().getItems().removeIf(item -> e.getRemoved().contains(item.getTree()));
				}
			}
		});

		controller.disableAllShowProperty().bind(Bindings.isEmpty(document.getNetworks()));

		RecentFilesManager.getInstance().setFileOpener(FileOpenManager.getFileOpener());
		RecentFilesManager.getInstance().setupMenu(controller.getRecentFilesMenu());

		controller.getUseAllMenuItem().setOnAction(e -> {
			for (var row : getSelectedOrAllRows(controller.getTreeTable())) {
				row.setUse(true);
			}
		});
		controller.getUseAllMenuItem().disableProperty().bind(Bindings.isEmpty(document.getTrees()));

		controller.getUseNoneMenuItem().setOnAction(e -> {
			for (var row : getSelectedOrAllRows(controller.getTreeTable())) {
				row.setUse(false);
			}
		});
		controller.getUseNoneMenuItem().disableProperty().bind(Bindings.isEmpty(document.getTrees()));

		controller.getShowAllMenuItem().setOnAction(e -> {
			for (var row : getSelectedOrAllRows(controller.getTreeTable())) {
				row.setShow(true);
			}
		});
		controller.getShowAllMenuItem().disableProperty().bind(Bindings.isEmpty(document.getNetworks()));

		controller.getShowNoneMenuItem().setOnAction(e -> {
			for (var row : getSelectedOrAllRows(controller.getTreeTable())) {
				row.setShow(false);
			}
		});
		controller.getShowNoneMenuItem().disableProperty().bind(Bindings.isEmpty(document.getNetworks()));

		controller.getQuitMenuItem().setOnAction((e) -> {
			while (MainWindowManager.getInstance().size() > 0) {
				final MainWindow aWindow = (MainWindow) MainWindowManager.getInstance().getMainWindow(MainWindowManager.getInstance().size() - 1);
				if (SaveBeforeClosingDialog.apply(aWindow) == SaveBeforeClosingDialog.Result.cancel || !MainWindowManager.getInstance().closeMainWindow(aWindow))
					break;
			}
		});

		var service = new PhyloFusionService(controller.getBottomFlowPane());
		service.setOnSucceeded(e -> {
			document.getNetworks().setAll(service.getValue());
		});

		controller.getRunMenuItem().setOnAction(e -> {
			var trees = new ArrayList<PhyloTree>();
			for (var row : controller.getTreeTable().getItems()) {
				if (row.isUse()) {
					trees.add(row.getTree());
				}
			}
			service.setupCalculation(trees, NumberUtils.parseDouble(controller.getConfidenceTextField().getText()));
			service.restart();
		});
		controller.getRunMenuItem().disableProperty().bind(Bindings.isEmpty(document.getTrees()).or(service.runningProperty()));
	}

	public static Collection<TreeRow> getSelectedOrAllRows(TableView<TreeRow> treeTableView) {
		if (treeTableView.getSelectionModel().getSelectedItems().isEmpty()) {
			return treeTableView.getItems();
		} else {
			return treeTableView.getSelectionModel().getSelectedItems();
		}
	}
}
