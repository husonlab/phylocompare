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
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import jloda.fx.dialog.SetParameterInternalDialog;
import jloda.fx.util.*;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.SplashScreen;
import jloda.fx.window.WindowGeometry;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.BitSetUtils;
import jloda.util.NumberUtils;
import phylofusion.algorithm.PhyloFusionService;
import phylofusion.io.SaveBeforeClosingDialog;
import phylofusion.trace.TreeTracerService;
import phylofusion.utils.DoubleSpinnerBinder;
import phylofusion.utils.SplitPaneSupport;
import phylofusion.view.NetworkView;
import splitstree6.layout.tree.TreeDiagramType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainWindowPresenter {
	private final MainWindow mainWindow;

	public MainWindowPresenter(MainWindow window) {
		this.mainWindow = window;
		var controller = window.getController();
		var document = window.getDocument();
		var treeRows = controller.getTreeTable().getItems();
		var noTrees = Bindings.isEmpty(document.getTrees());
		var noNetworks = Bindings.isEmpty(document.getNetworks());
		var scaleFactor = new SimpleDoubleProperty(this, "scaleFactor", 1.0);

		var networkView = new NetworkView(controller.getBottomFlowPane(), controller.getLegendVBox());
		Runnable updateView = () -> {
			RunAfterAWhile.applyInFXThread(networkView, () -> {
				if (document.getNetworks().isEmpty())
					networkView.clear();
				else {
					var network = document.getNetworks().get(0);
					networkView.update(network, scaleFactor.get());
				}
			});
		};

		DoubleSpinnerBinder.setupAndBind(controller.getOutlineWidthSpinner(), networkView.optionOutlineWidthProperty(), 0, 100, 30, 1);
		controller.getOutlineWidthSpinner().disableProperty().bind(noNetworks);

		networkView.optionOutlineWidthProperty().addListener(e -> updateView.run());
		networkView.optionAveragingProperty().addListener(e -> updateView.run());
		networkView.optionDiagramProperty().addListener(e -> updateView.run());

		var stackPane = new StackPane(networkView);
		stackPane.setPadding(new Insets(25));
		networkView.targetWidthProperty().bind(controller.getCenterPane().widthProperty());
		networkView.targetHeightProperty().bind(controller.getCenterPane().heightProperty());
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
						var id = treeRows.size() + 1; // todo: this won't work if we really add and remove trees
						var item = new TreeRow(tree.getName(), id, true, false, tree);
						treeRows.add(item);
					}
				}
				if (e.wasRemoved()) {
					treeRows.removeIf(item -> e.getRemoved().contains(item.getTree()));
				}
			}
		});

		controller.disableAllShowProperty().bind(noNetworks);

		RecentFilesManager.getInstance().setFileOpener(FileOpenManager.getFileOpener());
		RecentFilesManager.getInstance().setupMenu(controller.getRecentFilesMenu());

		controller.getShowAllMenuItem().setOnAction(e -> {
			for (var row : getSelectedOrAllRows(controller.getTreeTable())) {
				row.setShow(true);
			}
		});
		controller.getShowAllMenuItem().disableProperty().bind(noNetworks);

		controller.getShowNoneMenuItem().setOnAction(e -> {
			for (var row : getSelectedOrAllRows(controller.getTreeTable())) {
				row.setShow(false);
			}
		});
		controller.getShowNoneMenuItem().disableProperty().bind(noNetworks);

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
			for (var row : treeRows) {
				if (row.isRun()) {
					trees.add(row.getTree());
				}
			}
			service.setupCalculation(trees, NumberUtils.parseDouble(controller.getConfidenceTextField().getText()));
			service.restart();
		});
		controller.getRunMenuItem().disableProperty().bind(service.runningProperty().or(noTrees));


		InvalidationListener updateStatusLine = e -> {
			RunThrottled.apply("status", () -> {
				var buf = new StringBuilder();
				if (!document.getTrees().isEmpty()) {
					var active = treeRows.stream().filter(TreeRow::isRun).count();
					buf.append("Trees: %,d".formatted(active));
					if (active != document.getTrees().size())
						buf.append(" (of %,d)".formatted(document.getTrees().size()));
				}
				if (!document.getNetworks().isEmpty()) {
					var showing = treeRows.stream().filter(TreeRow::isShow).count();
					if (showing > 0)
						buf.append(" %,d shown".formatted(showing));

					var network = document.getNetworks().get(0);
					buf.append(". PhyloFusion network: ").append(RootedNetworkProperties.computeInfoString(network));
					if (document.getNetworks().size() > 1)
						buf.append(" (%,d networks)".formatted(document.getNetworks().size()));
				}
				controller.getStatusLabel().setText(buf.toString());
			});
		};
		document.getTrees().addListener(updateStatusLine);
		document.getNetworks().addListener(updateStatusLine);
		service.runningProperty().addListener(updateStatusLine);

		controller.getUseAllMenuItem().setOnAction(e -> {
			var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
			var used = selected.stream().filter(TreeRow::isRun).count();
			if (!selected.isEmpty() && used < selected.size())
				selected.forEach(row -> row.setRun(true));
			else
				treeRows.forEach(row -> row.setRun(true));
		});
		controller.getUseAllMenuItem().disableProperty().bind(service.runningProperty().or(noTrees));

		controller.getUseNoneMenuItem().setOnAction(e -> {
			var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
			var used = selected.stream().filter(TreeRow::isRun).count();
			if (!selected.isEmpty() && used > 0)
				selected.forEach(row -> row.setRun(false));
			else
				treeRows.forEach(row -> row.setRun(false));
		});
		controller.getUseNoneMenuItem().disableProperty().bind(service.runningProperty().or(noTrees));

		controller.getSetConfidenceThresholdMenuItem().setOnAction(e -> {
			var dialog = new SetParameterInternalDialog(controller.getCenterAnchorPane(), "Confidence", "Enter min edge confidence", "0.0", s -> {
				controller.getConfidenceTextField().setText(s);
			});
			dialog.show();
		});
		controller.getSetConfidenceThresholdMenuItem().disableProperty().bind(service.runningProperty().or(noTrees));

		SplitPaneSupport.installKeepLeftSameDuringWindowResize(controller.getRootPane(), controller.getSplitPane());

		controller.getSelectAllTableButton().setOnAction(e -> {
			controller.getTreeTable().getSelectionModel().selectAll();
		});
		controller.getSelectAllTableButton().disableProperty().bind(noTrees);

		controller.getSelectNoneTableButton().setOnAction(e -> {
			controller.getTreeTable().getSelectionModel().clearSelection();
		});
		controller.getSelectNoneTableButton().disableProperty().bind(noTrees);

		var treeTracerService = new TreeTracerService(controller.getBottomFlowPane());
		controller.getShowButton().setOnAction(e -> {
			treeTracerService.setupCalculation(document.getNetworks().get(0), treeRows, NumberUtils.parseDouble(controller.getConfidenceTextField().getText()));
			treeTracerService.setOnSucceeded(a -> {
				var trees = BitSetUtils.asBitSet(treeRows.stream().filter(TreeRow::isShow).mapToInt(TreeRow::getId).toArray());
				networkView.drawTracedTrees(document.getNetworks().get(0), treeRows, trees);
			});
			treeTracerService.restart();
			updateStatusLine.invalidated(null);
		});
		controller.getShowButton().disableProperty().bind(noNetworks.or(treeTracerService.runningProperty()).or(controller.getRunButton().disableProperty()));

		{
			controller.getRectangularCladogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.RectangularCladogram));
			controller.getRectangularCladogramMenuItem().disableProperty().bind(noNetworks);
			controller.getCircularCladogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.CircularCladogram));
			controller.getCircularCladogramMenuItem().disableProperty().bind(noNetworks);
			controller.getRadialCladogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.RadialCladogram));
			controller.getRadialCladogramMenuItem().disableProperty().bind(noNetworks);

			var menuButton = controller.getDiagramMenuButton();
			menuButton.setPrefWidth(50);
			menuButton.setMinWidth(Pane.USE_PREF_SIZE);
			menuButton.setMaxWidth(Pane.USE_PREF_SIZE);
			menuButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

			for (var diagramType : List.of(TreeDiagramType.RectangularCladogram, TreeDiagramType.CircularCladogram, TreeDiagramType.RadialCladogram)) {
				var radioButton = new RadioMenuItem();
				radioButton.setGraphic(diagramType.icon());
				radioButton.setOnAction(e -> networkView.setOptionDiagram(diagramType));
				networkView.optionDiagramProperty().addListener((v, o, n) -> {
					radioButton.setSelected(n == diagramType);
				});
				radioButton.disableProperty().bind(menuButton.disableProperty());
				menuButton.getItems().add(radioButton);
			}

			networkView.optionDiagramProperty().addListener((v, o, n) -> {
				if (n != null)
					menuButton.setGraphic(n.icon());
			});
			if (networkView.getOptionDiagram() != null)
				menuButton.setGraphic(networkView.getOptionDiagram().icon());
			menuButton.disableProperty().bind(noNetworks);
		}

		controller.getCloseMenuItem().setOnAction(e -> {
			if (SaveBeforeClosingDialog.apply(window) != SaveBeforeClosingDialog.Result.cancel) {
				ProgramProperties.put("WindowGeometry", (new WindowGeometry(window.getStage())).toString());
				MainWindowManager.getInstance().closeMainWindow(window);
			}
		});

		controller.getZoomInMenuItem().setOnAction(e -> {
			scaleFactor.set(1.1 * scaleFactor.get());
		});
		controller.getZoomInMenuItem().disableProperty().bind(noNetworks);
		controller.getZoomOutMenuItem().setOnAction(e -> {
			scaleFactor.set(1 / 1.1 * scaleFactor.get());
		});
		controller.getZoomOutMenuItem().disableProperty().bind(noNetworks);
		scaleFactor.addListener(e -> updateView.run());
	}

	public static Collection<TreeRow> getSelectedOrAllRows(TableView<TreeRow> treeTableView) {
		if (treeTableView.getSelectionModel().getSelectedItems().isEmpty()) {
			return treeTableView.getItems();
		} else {
			return treeTableView.getSelectionModel().getSelectedItems();
		}
	}


}
