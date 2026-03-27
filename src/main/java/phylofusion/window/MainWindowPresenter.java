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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import jloda.fx.control.RichTextLabel;
import jloda.fx.dialog.ExportImageDialog;
import jloda.fx.dialog.SetParameterInternalDialog;
import jloda.fx.util.*;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.SplashScreen;
import jloda.fx.window.WindowGeometry;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import phylofusion.algorithm.AlgorithmsService;
import phylofusion.io.ExportNewick;
import phylofusion.io.ImportNewick;
import phylofusion.io.Save;
import phylofusion.io.SaveBeforeClosingDialog;
import phylofusion.utils.DoubleSpinnerBinder;
import phylofusion.utils.SplitPaneSupport;
import phylofusion.view.NetworkView;
import splitstree6.layout.tree.TreeDiagramType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class MainWindowPresenter {
	private final MainWindow window;
	private final Runnable updateNetworkDrawing;
	private final Runnable updateTreesDrawing;

	private final AlgorithmsService algorithmsService;
	private final NetworkView networkView;


	public MainWindowPresenter(MainWindow window) {
		this.window = window;
		var controller = window.getController();
		var document = window.getDocument();
		var undoManager = window.getUndoManager();

		var confidenceThreshold = document.confidenceThresholdProperty();
		ProgramProperties.track(confidenceThreshold, 70.0);

		var scaleFactor = new SimpleDoubleProperty(this, "scaleFactor", 1.0);

		var canRun = document.hasTreesProperty();
		var canShowNetwork = document.hasNetworksProperty();

		controller.getTreeTable().setItems(document.getTreeRecords());

		controller.getConfidenceTextField().setOnAction(e -> {
			var text = controller.getConfidenceTextField().getText();
			if (NumberUtils.isDouble(text)) {
				var value = Math.max(Double.parseDouble(text), 0.0);
				confidenceThreshold.set(value);
			}
		});
		controller.getConfidenceTextField().setText(StringUtils.removeTrailingZerosAfterDot(confidenceThreshold.get()));
		controller.getConfidenceTextField().disableProperty().bind(canRun.not());
		confidenceThreshold.addListener(e -> controller.getRunMenuItem().fire());

		networkView = new NetworkView(controller.getBottomFlowPane(), controller.getLegendVBox());

		updateNetworkDrawing = () -> RunAfterAWhile.applyInFXThread("updateNetworkDrawing", () -> {
			if (document.getNetworks().isEmpty())
				networkView.clear();
			else {
				var network = document.getNetworks().get(0);
				networkView.update(document.getTreeRecords(), network, scaleFactor.get(), true, true);
			}
		});
		document.getNetworks().addListener((InvalidationListener) e -> updateNetworkDrawing());

		updateTreesDrawing = () -> RunAfterAWhile.applyInFXThread("updateTreesDrawing", () -> {
			if (document.getNetworks().isEmpty())
				networkView.clearTracedTreesDrawing();
			else {
				var network = document.getNetworks().get(0);
				networkView.update(document.getTreeRecords(), network, scaleFactor.get(), false, true);
			}
			// todo: need to implement selection of which network to draw
		});
		document.getTreeRecords().addListener((InvalidationListener) e -> updateNetworkDrawing());

		algorithmsService = new AlgorithmsService(controller.getBottomFlowPane());
		algorithmsService.setOnSucceeded(e -> {
			scaleFactor.set(1.0);
			Platform.runLater(updateNetworkDrawing);
		});

		controller.getRunMenuItem().setOnAction(e -> {
			if (!algorithmsService.isRunning()) {
				this.window.getUndoManager().clear();
				algorithmsService.setupCalculation(this.window, true, true);
				algorithmsService.setOnScheduled(a -> networkView.clear());
				algorithmsService.setOnSucceeded(a -> {
					updateNetworkDrawing();
					window.dirtyProperty().set(true);
				});
				algorithmsService.restart();
			}
		});
		controller.getRunMenuItem().disableProperty().bind(algorithmsService.runningProperty().or(document.hasTreesProperty().not()));

		DoubleSpinnerBinder.setupAndBind(controller.getOutlineWidthSpinner(), networkView.optionOutlineWidthProperty(), 0, 100, networkView.getOptionOutlineWidth(), 1);
		controller.getOutlineWidthSpinner().disableProperty().bind(canShowNetwork.not());

		networkView.optionOutlineWidthProperty().addListener((v, o, n) -> {
			updateNetworkDrawing();
			undoManager.add("width", networkView.optionOutlineWidthProperty(), o, n);
		});
		networkView.optionAveragingProperty().addListener((v, o, n) -> {
			updateNetworkDrawing();
			undoManager.add("averaging", networkView.optionAveragingProperty(), o, n);
		});
		networkView.optionDiagramProperty().addListener((v, o, n) -> {
			updateNetworkDrawing();
			undoManager.add("diagram", networkView.optionDiagramProperty(), o, n);
		});
		networkView.optionShowOutlineProperty().addListener((v, o, n) -> {
			undoManager.add("outline", networkView.optionShowOutlineProperty(), o, n);
		});
		networkView.optionShowOutlineProperty().bindBidirectional(controller.getOutlineCBox().selectedProperty());
		controller.getOutlineCBox().disableProperty().bind(document.hasNetworksProperty().not().or(algorithmsService.runningProperty()));

		networkView.optionReticulateEdgesAreSpecialProperty().bindBidirectional(controller.getReticulateEdgesAreSpecialCheckMenuItem().selectedProperty());
		networkView.optionReticulateEdgesAreSpecialProperty().addListener((v, o, n) -> {
			if (document.hasNetworks())
				updateNetworkDrawing();
		});

		window.getStage().widthProperty().addListener(e -> updateNetworkDrawing());
		window.getStage().heightProperty().addListener(e -> updateNetworkDrawing());

		var stackPane = new StackPane(networkView);
		stackPane.setPadding(new Insets(25));

		networkView.targetWidthProperty().bind(controller.getCenterPane().widthProperty());
		networkView.targetHeightProperty().bind(controller.getCenterPane().heightProperty());
		controller.getScrollPane().setContent(stackPane);

		controller.getUseDarkThemeCheckMenuItem().selectedProperty().bindBidirectional(MainWindowManager.useDarkThemeProperty());
		controller.getUseDarkThemeCheckMenuItem().setSelected(MainWindowManager.isUseDarkTheme());
		MainWindowManager.useDarkThemeProperty().addListener(e -> updateNetworkDrawing.run());
		controller.getUseDarkThemeCheckMenuItem().setDisable(false);

		if (this.window.getStage() != null)
			BasicFX.setupFullScreenMenuSupport(this.window.getStage(), controller.getFullScreenMenuItem());

		controller.getTreeTable().getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		controller.getAboutMenuItem().setOnAction(e -> SplashScreen.showSplash(Duration.ofSeconds(30)));

		controller.getNewMenuItem().setOnAction(e -> NewWindow.apply());
		controller.getOpenMenuItem().setOnAction(FileOpenManager.createOpenFileEventHandler(window.getStage()));

		controller.disableAllRunProperty().bind(document.hasTreesProperty().not().or(algorithmsService.runningProperty()));
		controller.disableAllShowProperty().bind(document.hasTreeRecordsProperty().not().or(algorithmsService.runningProperty()));

		RecentFilesManager.getInstance().setFileOpener(FileOpenManager.getFileOpener());
		RecentFilesManager.getInstance().setupMenu(controller.getRecentFilesMenu());

		window.getStage().setOnCloseRequest(e -> {
			controller.getCloseMenuItem().getOnAction().handle(null);
			e.consume();
		});

		controller.getShowAllMenuItem().setOnAction(e -> {
			var showing = document.getShowTrees();
			undoManager.doAndAdd("show", () -> {
				for (var record : document.getTreeRecords()) {
					record.setShow(showing.get(record.getId()));
				}
			}, () -> {
				for (var row : getSelectedRowsOrAll(controller.getTreeTable(), document.getTreeRecords())) {
					row.setShow(true);
				}
			});
		});
		controller.getShowAllMenuItem().disableProperty().bind(document.hasTreeRecordsProperty().not().or(algorithmsService.runningProperty()));

		controller.getShowNoneMenuItem().setOnAction(e -> {
			var showing = document.getShowTrees();
			undoManager.doAndAdd("show", () -> {
				for (var record : document.getTreeRecords()) {
					record.setShow(showing.get(record.getId()));
				}
			}, () -> {
				for (var row : getSelectedRowsOrAll(controller.getTreeTable(), document.getTreeRecords())) {
					row.setShow(false);
				}
			});
		});
		controller.getShowNoneMenuItem().disableProperty().bind(document.hasTreeRecordsProperty().not().or(algorithmsService.runningProperty()));

		controller.getQuitMenuItem().setOnAction((e) -> {
			while (MainWindowManager.getInstance().size() > 0) {
				final MainWindow aWindow = (MainWindow) MainWindowManager.getInstance().getMainWindow(MainWindowManager.getInstance().size() - 1);
				if (SaveBeforeClosingDialog.apply(aWindow) == SaveBeforeClosingDialog.Result.cancel || !MainWindowManager.getInstance().closeMainWindow(aWindow))
					break;
			}
		});

		InvalidationListener updateStatusLine = e -> {
			RunAfterAWhile.applyInFXThread("status", () -> {
				var buf = new StringBuilder();
				if (!document.getTreeRecords().isEmpty()) {
					var active = document.getTreeRecords().stream().filter(TreeRecord::isRun).count();
					buf.append("Trees: %,d".formatted(active));
					if (active != document.getTreeRecords().size())
						buf.append(" (of %,d)".formatted(document.getTreeRecords().size()));
				}
				if (!document.getNetworks().isEmpty()) {
					var showing = document.getTreeRecords().stream().filter(TreeRecord::isShow).count();
					if (showing > 0)
						buf.append(", showing %,d".formatted(showing));

					var network = document.getNetworks().get(0);
					buf.append(". Network: ").append(RootedNetworkProperties.computeInfoString(network));
					if (document.getNetworks().size() > 1)
						buf.append(" (%,d networks)".formatted(document.getNetworks().size()));
				}
				controller.getStatusLabel().setText(buf.toString());
			});
		};
		document.getTreeRecords().addListener(updateStatusLine);
		document.getNetworks().addListener(updateStatusLine);
		algorithmsService.runningProperty().addListener(updateStatusLine);

		controller.getUseAllMenuItem().setOnAction(e -> {
			var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
			var used = selected.stream().filter(TreeRecord::isRun).count();
			if (!selected.isEmpty() && used < selected.size())
				selected.forEach(row -> row.setRun(true));
			else
				document.getTreeRecords().forEach(row -> row.setRun(true));
		});
		controller.getUseAllMenuItem().disableProperty().bind(algorithmsService.runningProperty().or(document.hasTreeRecordsProperty().not()).or(algorithmsService.runningProperty()));

		controller.getUseNoneMenuItem().setOnAction(e -> {
			var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
			var used = selected.stream().filter(TreeRecord::isRun).count();
			if (!selected.isEmpty() && used > 0)
				selected.forEach(row -> row.setRun(false));
			else
				document.getTreeRecords().forEach(row -> row.setRun(false));
		});
		controller.getUseNoneMenuItem().disableProperty().bind(algorithmsService.runningProperty().or(document.hasTreeRecordsProperty().not()).or(algorithmsService.runningProperty()));

		controller.getSetConfidenceThresholdMenuItem().setOnAction(e -> {
			var dialog = new SetParameterInternalDialog(controller.getCenterAnchorPane(), "Confidence", "Enter min edge confidence", "0.0", s -> {
				controller.getConfidenceTextField().setText(s);
			});
			dialog.show();
		});
		controller.getSetConfidenceThresholdMenuItem().disableProperty().bind(algorithmsService.runningProperty().or(document.hasTreesProperty().not()).or(algorithmsService.runningProperty()));

		SplitPaneSupport.installKeepLeftSameDuringWindowResize(controller.getRootPane(), controller.getSplitPane());

		controller.getSelectAllTableButton().setOnAction(e -> {
			controller.getTreeTable().getSelectionModel().selectAll();
		});
		controller.getSelectAllTableButton().disableProperty().bind(document.hasTreeRecordsProperty().not().or(algorithmsService.runningProperty()));

		controller.getSelectNoneTableButton().setOnAction(e -> {
			controller.getTreeTable().getSelectionModel().clearSelection();
		});
		controller.getSelectNoneTableButton().disableProperty().bind(document.hasTreeRecordsProperty().not().or(algorithmsService.runningProperty()));

		controller.getShowSelectedMenuItem().setOnAction(e -> {
			if (!controller.getTreeTable().getSelectionModel().getSelectedItems().isEmpty()) {
				var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
				for (var record : document.getTreeRecords()) {
					record.setShow(selected.contains(record));
				}
			}
			updateTreesDrawing();
			updateStatusLine.invalidated(null);
			window.dirtyProperty().set(true);
		});
		controller.getShowSelectedMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(networkView.runningProperty()).or(algorithmsService.runningProperty()).or(document.hasTreeRecordsProperty().not()));

		controller.getShowButton().setOnAction(e -> {
			updateTreesDrawing();
			updateStatusLine.invalidated(null);
			window.dirtyProperty().set(true);
		});
		controller.getShowButton().disableProperty().bind(controller.getShowSelectedMenuItem().disableProperty());

		{
			controller.getRectangularCladogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.RectangularCladogram));
			controller.getRectangularCladogramMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(algorithmsService.runningProperty()));
			controller.getCircularCladogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.CircularCladogram));
			controller.getCircularCladogramMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(algorithmsService.runningProperty()));
			controller.getRadialCladogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.RadialCladogram));
			controller.getRadialCladogramMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(algorithmsService.runningProperty()));

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
			menuButton.disableProperty().bind(document.hasNetworksProperty().not().or(algorithmsService.runningProperty()));
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
		controller.getZoomInMenuItem().disableProperty().bind(document.hasNetworksProperty().not());
		controller.getZoomOutMenuItem().setOnAction(e -> {
			scaleFactor.set(1 / 1.1 * scaleFactor.get());
		});
		controller.getZoomOutMenuItem().disableProperty().bind(document.hasNetworksProperty().not());

		controller.getZoomToFitMenuItem().setOnAction(e -> {
			scaleFactor.set(1.0);
		});

		scaleFactor.addListener(e -> updateNetworkDrawing.run());

		controller.getIncreaseFontSizeMenuItem().setOnAction(e -> {
			for (var label : BasicFX.getAllRecursively(networkView, RichTextLabel.class)) {
				label.setFontSize(Math.min(128, 1.1 * label.getFontSize()));
			}
		});
		controller.getIncreaseFontSizeMenuItem().disableProperty().bind(document.hasNetworksProperty().not());
		controller.getDecreaseFontSizeMenuItem().setOnAction(e -> {
			for (var label : BasicFX.getAllRecursively(networkView, RichTextLabel.class)) {
				label.setFontSize(Math.max(4, 1 / 1.1 * label.getFontSize()));
			}
		});
		controller.getDecreaseFontSizeMenuItem().disableProperty().bind(document.hasNetworksProperty().not());

		controller.getCopyImageMenuItem().setOnAction(e -> {
			var hPolicy = controller.getScrollPane().getHbarPolicy();
			var vPolicy = controller.getScrollPane().getVbarPolicy();
			try {
				controller.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
				controller.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
				ClipboardUtils.putImage(controller.getInnerAnchorPane());
			} finally {
				controller.getScrollPane().setHbarPolicy(hPolicy);
				controller.getScrollPane().setVbarPolicy(vPolicy);
			}
		});
		controller.getCopyImageMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(algorithmsService.runningProperty()));

		controller.getExportImageMenuItem().setOnAction(e -> {
			var hPolicy = controller.getScrollPane().getHbarPolicy();
			var vPolicy = controller.getScrollPane().getVbarPolicy();
			try {
				controller.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
				controller.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
				ExportImageDialog.show(window.getFileName(), window.getStage(), controller.getInnerAnchorPane());
			} finally {
				controller.getScrollPane().setHbarPolicy(hPolicy);
				controller.getScrollPane().setVbarPolicy(vPolicy);
			}
		});
		controller.getExportImageMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(algorithmsService.runningProperty()));

		controller.getExportNewickMenuItem().setOnAction(e -> ExportNewick.apply(window));
		controller.getExportNewickMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(algorithmsService.runningProperty()));

		controller.getSaveMenuItem().setOnAction(e -> Save.showSaveDialog(this.window));
		controller.getSaveMenuItem().disableProperty().bind(document.emptyProperty().or(algorithmsService.runningProperty()));

		controller.getUndoMenuItem().setOnAction(e -> undoManager.undo());
		controller.getUndoMenuItem().textProperty().bind(undoManager.undoNameProperty());
		controller.getUndoMenuItem().disableProperty().bind(undoManager.undoableProperty().not().or(algorithmsService.runningProperty()));

		controller.getRedoMenuItem().setOnAction(e -> undoManager.redo());
		controller.getRedoMenuItem().textProperty().bind(undoManager.redoNameProperty());
		controller.getRedoMenuItem().disableProperty().bind(undoManager.redoableProperty().not().or(algorithmsService.runningProperty()));

		controller.getCopyMenuItem().setOnAction(e -> {
			try {
				if (document.hasNetworks())
					ClipboardUtils.putString(ExportNewick.apply(document.getNetwork()));
				else if (document.hasTreesProperty().get()) {
					var trees = getSelectedRowsOrAll(controller.getTreeTable(), document.getTreeRecords()).stream().map(TreeRecord::getTree).filter(Objects::nonNull).toList();
					if (!trees.isEmpty())
						ClipboardUtils.putString(ExportNewick.apply(trees));
				}
			} catch (IOException ex) {
				System.err.println(ex.getMessage());
			}
		});
		controller.getCopyMenuItem().disableProperty().bind(document.emptyProperty().or(algorithmsService.runningProperty()));

		controller.getClearMenuItem().setOnAction(e -> {
			networkView.clear();
			document.getNetworks().clear();
		});
		controller.getClearMenuItem().disableProperty().bind((document.hasTreesProperty().and(document.hasNetworksProperty()).not()));

		var canEditTreesList = new SimpleBooleanProperty(this, "canEditTreesList", false);
		canEditTreesList.bind(document.hasTreesProperty().and(document.hasNetworksProperty().not()).or(algorithmsService.runningProperty().not()));

		controller.getPasteMenuItem().setOnAction(e -> {
			if (ClipboardUtils.hasString()) {
				try {
					document.addTrees(ImportNewick.apply(new BufferedReader(new StringReader(ClipboardUtils.getString()))));
				} catch (IOException ignored) {
				}
			}
		});
		controller.getPasteMenuItem().disableProperty().bind(canEditTreesList.not().and(document.emptyProperty().not()));

		controller.getDeleteMenuItem().setOnAction(e -> document.getTreeRecords().removeAll(getSelectedRowsOrAll(controller.getTreeTable(), document.getTreeRecords())));
		controller.getDeleteMenuItem().disableProperty().bind(canEditTreesList.not());

		WindowMenuSetup.setup(controller.getWindowMenu(), window.fileNameProperty());
	}

	public static Collection<TreeRecord> getSelectedRowsOrAll(TableView<TreeRecord> treeTableView, List<TreeRecord> treeRecords) {
		if (treeTableView.getSelectionModel().getSelectedItems().isEmpty()) {
			return treeRecords;
		} else {
			return treeTableView.getSelectionModel().getSelectedItems();
		}
	}

	public void updateNetworkDrawing() {
		updateNetworkDrawing.run();
	}

	public void updateTreesDrawing() {
		if (!algorithmsService.isRunning()) {
			algorithmsService.setupCalculation(window, false, true);
			algorithmsService.setOnSucceeded(a -> {
				updateTreesDrawing.run();
				window.dirtyProperty().set(true);
			});
			algorithmsService.restart();
		}
	}

	public NetworkView getNetworkView() {
		return networkView;
	}
}
