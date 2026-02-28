

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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import jloda.fx.dialog.SetParameterInternalDialog;
import jloda.fx.util.*;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.SplashScreen;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.phylogeny.layout.Averaging;
import jloda.util.NumberUtils;
import phylofusion.algorithm.PhyloFusionService;
import phylofusion.io.SaveBeforeClosingDialog;
import phylofusion.utils.DoubleSpinnerBinder;
import phylofusion.utils.SplitPaneSupport;
import phylofusion.view.NetworkView;
import splitstree6.layout.tree.TreeDiagramType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;

public class MainWindowPresenter {
	private final MainWindow mainWindow;

	private final DoubleProperty outlineWidth = new SimpleDoubleProperty(this, "outlineWidth", 30);

	public MainWindowPresenter(MainWindow window) {
		this.mainWindow = window;
		var controller = window.getController();
		var document = window.getDocument();

		var networkView = new NetworkView(controller.getBottomFlowPane());
		Runnable updateView = () -> {
			if (document.getNetworks().isEmpty())
				networkView.clear();
			else {
				var network = document.getNetworks().get(0);
				networkView.update(network, TreeDiagramType.RectangularCladogram, Averaging.ChildAverage, outlineWidth.get());
			}
		};

		DoubleSpinnerBinder.setupAndBind(controller.getOutlineWidthSpinner(), outlineWidth, 0, 100, 30, 1);
		outlineWidth.addListener((v, o, n) -> {
			if (!document.getNetworks().isEmpty()) {
				RunAfterAWhile.applyInFXThread(outlineWidth, () -> {
					networkView.drawOutline(n.doubleValue());
				});
			}
		});
		controller.getOutlineWidthSpinner().disableProperty().bind(Bindings.isEmpty(document.getNetworks()));

		var stackPane = new StackPane(networkView);
		stackPane.setPadding(new Insets(25));
		networkView.prefWidthProperty().bind(controller.getCenterPane().widthProperty());
		networkView.prefHeightProperty().bind(controller.getCenterPane().heightProperty());
		controller.getScrollPane().setContent(stackPane);

		controller.getUseDarkThemeCheckMenuItem().selectedProperty().bindBidirectional(MainWindowManager.useDarkThemeProperty());
		controller.getUseDarkThemeCheckMenuItem().setSelected(MainWindowManager.isUseDarkTheme());
		controller.getUseDarkThemeCheckMenuItem().setDisable(false);

		if (mainWindow.getStage() != null)
			BasicFX.setupFullScreenMenuSupport(mainWindow.getStage(), controller.getFullScreenMenuItem());

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
			Platform.runLater(updateView);
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

		var countActive = TableViewSupport.setupCountActive(controller.getTreeTable());

		InvalidationListener listener = e -> {
			RunThrottled.apply("status", () -> {
				var buf = new StringBuilder();
				if (!document.getTrees().isEmpty()) {
					var active = countActive.get();
					buf.append("Trees: %,d".formatted(active));
					if (active != document.getTrees().size())
						buf.append(" (of %,d)".formatted(document.getTrees().size()));
				}
				if (!document.getNetworks().isEmpty()) {
					var network = document.getNetworks().get(0);
					buf.append(". PhyloFusion network: ").append(RootedNetworkProperties.computeInfoString(network));
					if (document.getNetworks().size() > 1)
						buf.append(" (%,d networks)".formatted(document.getNetworks().size()));
				}
				controller.getStatusLabel().setText(buf.toString());
			});
		};
		document.getTrees().addListener(listener);
		document.getNetworks().addListener(listener);

		controller.getUseAllMenuItem().setOnAction(e -> {
			var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
			var used = selected.stream().filter(TreeRow::isUse).count();
			if (!selected.isEmpty() && used < selected.size())
				selected.forEach(row -> row.setUse(true));
			else
				controller.getTreeTable().getItems().forEach(row -> row.setUse(true));
		});
		controller.getUseAllMenuItem().disableProperty().bind(Bindings.isEmpty(document.getTrees()).or(service.runningProperty()));

		controller.getUseNoneMenuItem().setOnAction(e -> {
			var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
			var used = selected.stream().filter(TreeRow::isUse).count();
			if (!selected.isEmpty() && used > 0)
				selected.forEach(row -> row.setUse(false));
			else
				controller.getTreeTable().getItems().forEach(row -> row.setUse(false));
		});
		controller.getUseNoneMenuItem().disableProperty().bind(Bindings.isEmpty(document.getTrees()).or(service.runningProperty()));

		controller.getSetConfidenceThresholdMenuItem().setOnAction(e -> {
			var dialog = new SetParameterInternalDialog(controller.getCenterAnchorPane(), "Confidence", "Enter min edge confidence", "0.0", s -> {
				controller.getConfidenceTextField().setText(s);
			});
			dialog.show();
		});
		controller.getSetConfidenceThresholdMenuItem().disableProperty().bind(Bindings.isEmpty(document.getTrees()).or(service.runningProperty()));

		SplitPaneSupport.installKeepLeftSameDuringWindowResize(controller.getRootPane(), controller.getSplitPane());

		controller.getSelectAllTableButton().setOnAction(e -> {
			controller.getTreeTable().getSelectionModel().selectAll();
		});
		controller.getSelectAllTableButton().disableProperty().bind(Bindings.isEmpty(controller.getTreeTable().getItems()));

		controller.getSelectNoneTableButton().setOnAction(e -> {
			controller.getTreeTable().getSelectionModel().clearSelection();
		});
		controller.getSelectNoneTableButton().disableProperty().bind(Bindings.isEmpty(controller.getTreeTable().getItems()));
	}

	public static Collection<TreeRow> getSelectedOrAllRows(TableView<TreeRow> treeTableView) {
		if (treeTableView.getSelectionModel().getSelectedItems().isEmpty()) {
			return treeTableView.getItems();
		} else {
			return treeTableView.getSelectionModel().getSelectedItems();
		}
	}
}
