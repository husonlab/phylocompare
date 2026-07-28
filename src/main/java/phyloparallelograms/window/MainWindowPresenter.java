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

package phyloparallelograms.window;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.util.Pair;
import jloda.fx.control.RichTextLabel;
import jloda.fx.dialog.ConfirmInternalDialog;
import jloda.fx.dialog.ExportImageDialog;
import jloda.fx.dialog.SetParameterDialog;
import jloda.fx.dialog.SetParameterInternalDialog;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.options.OptionControls;
import jloda.fx.options.OptionsRegistry;
import jloda.fx.service.UpdateService;
import jloda.fx.undo.CoalescingUndo;
import jloda.fx.util.*;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.SplashScreen;
import jloda.fx.window.WindowGeometry;
import jloda.fx.windownotifications.WindowNotifications;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.FileUtils;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import phyloparallelograms.algorithm.AlgorithmsService;
import phyloparallelograms.algorithm.RemoveTaxaService;
import phyloparallelograms.algorithm.RerootService;
import phyloparallelograms.examples.ExamplesSupport;
import phyloparallelograms.io.*;
import phyloparallelograms.main.Version;
import phyloparallelograms.model.Document;
import phyloparallelograms.trace.BruteForceTreeTracer;
import phyloparallelograms.trace.TreeTrace;
import phyloparallelograms.utils.SplitPaneSupport;
import phyloparallelograms.view.Legend;
import phyloparallelograms.view.NetworkView;
import phyloparallelograms.view.ScaleDrawing;
import phyloparallelograms.view.SetupRubberBandSelection;
import splitstree6.algorithms.trees.trees2trees.PhyloFusion;
import splitstree6.compute.phylofusion.PhyloFusionAlgorithm;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.view.format.taxlabel.TaxonLabelFormat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainWindowPresenter {
	/**
	 * milliseconds to wait after the last change of a threshold before recomputing the network
	 */
	private static final long RECOMPUTE_DELAY = 500L;

	/**
	 * milliseconds to wait after the last change of a spinner-driven option before adding an undoable item
	 */
	private static final long UNDO_COALESCE_DELAY = 500L;

	private final MainWindow window;
	private final Runnable updateNetworkDrawing;
	private final Runnable updateTreesDrawing;

	private final AlgorithmsService algorithmsService;
	private final RerootService rerootService;
	private final RemoveTaxaService removeTaxaService;
	private final NetworkView networkView;
	private final OptionsRegistry optionsRegistry;

	private final MainWindowController controller;
	private final DoubleProperty scaleFactorX = new SimpleDoubleProperty(this, "scaleFactorX", 1.0);
	private final DoubleProperty scaleFactorY = new SimpleDoubleProperty(this, "scaleFactorY", 1.0);


	public MainWindowPresenter(MainWindow window) {
		this.window = window;
		controller = window.getController();
		var document = window.getDocument();
		var undoManager = window.getUndoManager();

		var confidenceThreshold = document.confidenceThresholdProperty();
		var concordanceThreshold = document.concordanceThresholdProperty();

		var focusOwner = window.getStage().getScene().focusOwnerProperty();

		SetupColorSchemes.apply(window);

		var canRun = document.hasTreesProperty();

		controller.getTreeTable().setItems(document.getTreeRecords());

		networkView = new NetworkView(window.getTaxaSelectionModel(), (Pane) controller.getScrollPane().getContent(), controller.getBottomFlowPane(), new Legend(window, controller.getLegendVBox()));

		optionsRegistry = OptionsRegistry.of(document, networkView);
		// the set of color schemes is only known at runtime, so cannot be declared in the annotation:
		optionsRegistry.setValidator("color_scheme_name", value -> ColorSchemeManager.getInstance().getNames().contains(value.toString()));

		OptionControls.bindSpinner(controller.getConfidenceSpinner(), optionsRegistry.get("confidence_threshold"), 1);
		controller.getConfidenceSpinner().disableProperty().bind(document.hasTreeConfidencesProperty().not().or(canRun.not()));
		CoalescingUndo.track(undoManager, "min branch confidence", confidenceThreshold, UNDO_COALESCE_DELAY);
		confidenceThreshold.addListener(e -> runRecomputeNetworkAfterAWhile());

		controller.getSetConfidenceThresholdMenuItem().setOnAction(e -> {
			var item = optionsRegistry.get("confidence_threshold");
			var dialog = new SetParameterInternalDialog(controller.getCenterAnchorPane(), "Confidence", OptionControls.tooltipText(item), item.getValueString(), item::setValueString);
			dialog.show();
		});
		controller.getSetConfidenceThresholdMenuItem().disableProperty().bind(controller.getConfidenceSpinner().disabledProperty());

		OptionControls.bindSpinner(controller.getConcordanceSpinner(), optionsRegistry.get("concordance_threshold"), 1);
		controller.getConcordanceSpinner().disableProperty().bind(canRun.not().or(Bindings.createBooleanBinding(() -> document.getRunTrees().size() < 5, document.updatedRunTreesProperty())));
		CoalescingUndo.track(undoManager, "min branch concordance", concordanceThreshold, UNDO_COALESCE_DELAY);
		concordanceThreshold.addListener(e -> runRecomputeNetworkAfterAWhile());

		controller.getSetCondordanceThresholdMenuItem().setOnAction(e -> {
			var item = optionsRegistry.get("concordance_threshold");
			var dialog = new SetParameterInternalDialog(controller.getCenterAnchorPane(), "Concordance", OptionControls.tooltipText(item), item.getValueString(), item::setValueString);
			dialog.show();
		});
		controller.getSetCondordanceThresholdMenuItem().disableProperty().bind(controller.getConcordanceSpinner().disabledProperty());

		updateNetworkDrawing = () -> RunAfterAWhile.applyInFXThread("runUpdateNetworkDrawing", () -> {
			if (document.getNetworks().isEmpty())
				networkView.clear();
			else {
				var network = document.getNetworks().get(0);
				var legendVisible = networkView.getLegend().isVisible();
				if (legendVisible)
					networkView.getLegend().setVisible(false);
				Platform.runLater(() -> {
					var treeRecords = controller.getTreeTable().getItems();
					networkView.update(document.getTaxaBlock(), treeRecords, network, scaleFactorX.get(), scaleFactorY.get(), true, true, document.getColorSchemeName());
					networkView.getLegend().setVisible(legendVisible);
				});

			}
		});
		document.getNetworks().addListener((InvalidationListener) e -> runUpdateNetworkDrawing());

		updateTreesDrawing = () -> RunAfterAWhile.applyInFXThread("runUpdateTreesDrawing", () -> {
			if (document.getNetworks().isEmpty())
				networkView.clearTracedTreesDrawing();
			else {
				var network = document.getNetworks().get(0);
				var treeRecords = controller.getTreeTable().getItems();
				networkView.update(document.getTaxaBlock(), treeRecords, network, scaleFactorX.get(), scaleFactorY.get(), false, true, document.getColorSchemeName());
			}
			// todo: need to implement selection of which network to draw
		});
		document.getTreeRecords().addListener((InvalidationListener) e -> runUpdateNetworkDrawing());
		document.colorSchemeNameProperty().addListener(e -> runUpdateTreesDrawing());

		setupNote(document);

		algorithmsService = new AlgorithmsService(controller.getBottomFlowPane());
		algorithmsService.setOnSucceeded(e -> {
			scaleFactorX.set(1.0);
			scaleFactorY.set(1.0);
			Platform.runLater(updateNetworkDrawing);
		});

		removeTaxaService = new RemoveTaxaService(controller.getBottomFlowPane());

		final var serviceRunning = new SimpleBooleanProperty(false);
		rerootService = new RerootService(controller.getBottomFlowPane());
		SetupReroot.apply(window, rerootService, serviceRunning);

		serviceRunning.bind(algorithmsService.runningProperty().or(rerootService.runningProperty()).or(networkView.runningProperty()).or(removeTaxaService.runningProperty()));

		controller.getRunMenuItem().setOnAction(e -> {
			if (!algorithmsService.isRunning()) {
				if (false) this.window.getUndoManager().clear();
				runRecomputeNetwork();
			}
		});
		controller.getRunMenuItem().disableProperty().bind(serviceRunning.or(document.hasTreesProperty().not()));

		OptionControls.bindSpinner(controller.getOutlineSpreadSpinner(), optionsRegistry.get("outline_width"), 1);

		OptionControls.bindSpinner(controller.getTransferAcceptorPercentSpinner(), optionsRegistry.get("acceptor_percentage"), 1);

		controller.getUseTransferMenuItem().selectedProperty().bindBidirectional(networkView.optionShowTransferProperty());

		controller.getAcceptorPercentMenuItem().setOnAction(e -> {
			var item = optionsRegistry.get("acceptor_percentage");
			var dialog = new SetParameterInternalDialog(controller.getCenterAnchorPane(), "Transfer threshold", OptionControls.tooltipText(item), item.getValueString(), item::setValueString);
			dialog.show();
		});
		controller.getAcceptorPercentMenuItem().disableProperty().bind(controller.getUseTransferMenuItem().disableProperty());

		// coalescing, so that a burst of spinner arrow presses gives one undoable item, not one per press:
		CoalescingUndo.track(undoManager, "transfer acceptor percent", networkView.optionAcceptorPercentageProperty(), UNDO_COALESCE_DELAY);
		networkView.optionAcceptorPercentageProperty().addListener(e -> runUpdateNetworkDrawing());
		networkView.optionShowTransferProperty().addListener(e -> runUpdateNetworkDrawing());

		networkView.optionShowTransferProperty().addListener((v, o, n) -> {
			undoManager.add("show transfers", networkView.optionShowTransferProperty(), o, n);
			runUpdateNetworkDrawing();
		});

		CoalescingUndo.track(undoManager, "width", networkView.optionOutlineWidthProperty(), UNDO_COALESCE_DELAY);
		networkView.optionOutlineWidthProperty().addListener(e -> runUpdateNetworkDrawing());
		networkView.optionAveragingProperty().addListener((v, o, n) -> {
			runUpdateNetworkDrawing();
			undoManager.add("averaging", networkView.optionAveragingProperty(), o, n);
		});
		networkView.optionDiagramProperty().addListener((v, o, n) -> {
			runUpdateNetworkDrawing();
			undoManager.add("diagram", networkView.optionDiagramProperty(), o, n);
		});
		networkView.optionShowOutlineProperty().addListener((v, o, n) -> {
			undoManager.add("outline", networkView.optionShowOutlineProperty(), o, n);
		});

		controller.getShowOutlineCheckMenuItem().selectedProperty().bindBidirectional(networkView.optionShowOutlineProperty());

		networkView.optionReticulateEdgesAreSpecialProperty().addListener(e -> {
			if (document.hasNetworks())
				runUpdateNetworkDrawing();
		});

		controller.getCurvedReticulateEdgesCheckMenuItem().setSelected(networkView.optionReticulateEdgesAreSpecialProperty().get());
		controller.getCurvedReticulateEdgesCheckMenuItem().selectedProperty().addListener((v, o, n) ->
				undoManager.doAndAdd("special edges", networkView.optionReticulateEdgesAreSpecialProperty(), o, n));

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

		controller.disableAllRunProperty().bind(document.hasTreesProperty().not().or(serviceRunning));
		controller.disableAllShowProperty().bind(document.hasTreeRecordsProperty().not().or(serviceRunning));

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
		controller.getShowAllMenuItem().disableProperty().bind(document.hasTreeRecordsProperty().not().or(serviceRunning));

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
		controller.getShowNoneMenuItem().disableProperty().bind(document.hasTreeRecordsProperty().not().or(serviceRunning));

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
					var active = document.getTreeRecords().stream().filter(TreeRecord::getRunLayout).count();
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
		serviceRunning.addListener(updateStatusLine);

		controller.getUseAllMenuItem().setOnAction(e -> {
			var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
			var used = selected.stream().filter(TreeRecord::getRunLayout).count();
			if (!selected.isEmpty() && used < selected.size())
				selected.forEach(row -> row.setRunLayout(true));
			else
				document.getTreeRecords().forEach(row -> row.setRunLayout(true));
		});
		controller.getUseAllMenuItem().disableProperty().bind(serviceRunning.or(document.hasTreeRecordsProperty().not()).or(serviceRunning));

		controller.getUseNoneMenuItem().setOnAction(e -> {
			var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
			var used = selected.stream().filter(TreeRecord::getRunLayout).count();
			if (!selected.isEmpty() && used > 0)
				selected.forEach(row -> row.setRunLayout(false));
			else
				document.getTreeRecords().forEach(row -> row.setRunLayout(false));
		});
		controller.getUseNoneMenuItem().disableProperty().bind(serviceRunning.or(document.hasTreeRecordsProperty().not()).or(serviceRunning));


		SplitPaneSupport.installKeepLeftSameDuringWindowResize(controller.getRootPane(), controller.getSplitPane());

		controller.getSelectTableButton().setOnAction(e -> {
			var treeTable = controller.getTreeTable();
			var treeSelectionModel = treeTable.getSelectionModel();
			if (treeSelectionModel.getSelectedItems().size() < treeTable.getItems().size())
				treeSelectionModel.selectAll();
			else
				treeSelectionModel.clearSelection();
			controller.getTreeTable().requestFocus();
		});
		controller.getSelectTableButton().disableProperty().bind(document.hasTreeRecordsProperty().not().or(serviceRunning));

		controller.getSelectTaxaButton().setOnAction(e -> {
			var taxaSelectionModel = window.getTaxaSelectionModel();
			if (taxaSelectionModel.size() < document.getTaxaBlock().getNtax())
				taxaSelectionModel.selectAll(document.getTaxaBlock().getTaxa());
			else taxaSelectionModel.clearSelection();
			controller.getCenterPane().requestFocus();
		});
		controller.getSelectTaxaButton().disableProperty().bind(document.hasNetworksProperty().not().or(serviceRunning));

		controller.getShowSelectedMenuItem().setOnAction(e -> {
			if (!controller.getTreeTable().getSelectionModel().getSelectedItems().isEmpty()) {
				var selected = controller.getTreeTable().getSelectionModel().getSelectedItems();
				for (var record : document.getTreeRecords()) {
					record.setShow(selected.contains(record));
				}
			}
			runUpdateTreesDrawing();
			updateStatusLine.invalidated(null);
			window.dirtyProperty().set(true);
		});
		controller.getShowSelectedMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(serviceRunning).or(document.hasTreeRecordsProperty().not()));

		controller.getShowButton().setOnAction(e -> {
			runUpdateTreesDrawing();
			updateStatusLine.invalidated(null);
			window.dirtyProperty().set(true);
		});
		controller.getShowButton().disableProperty().bind(controller.getShowSelectedMenuItem().disableProperty());

		{
			controller.getRectangularCladogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.RectangularCladogram));
			controller.getRectangularPhylogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.RectangularPhylogram));

			controller.getCircularCladogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.CircularCladogram));
			controller.getCircularPhylogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.CircularPhylogram));

			controller.getRadialCladogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.RadialCladogram));
			controller.getRadialPhylogramMenuItem().setOnAction(e -> networkView.setOptionDiagram(TreeDiagramType.RadialPhylogram));

			var menuButton = controller.getDiagramMenuButton();
			menuButton.setPrefWidth(50);
			menuButton.setMinWidth(Pane.USE_PREF_SIZE);
			menuButton.setMaxWidth(Pane.USE_PREF_SIZE);
			menuButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

			for (var diagramType : List.of(TreeDiagramType.RectangularCladogram, TreeDiagramType.RectangularPhylogram, TreeDiagramType.CircularCladogram, TreeDiagramType.CircularPhylogram, TreeDiagramType.RadialCladogram, TreeDiagramType.RadialPhylogram)) {
				var radioButton = new RadioMenuItem();
				radioButton.setGraphic(diagramType.icon());
				radioButton.setOnAction(e -> networkView.setOptionDiagram(diagramType));
				networkView.optionDiagramProperty().addListener((v, o, n) -> {
					radioButton.setSelected(n == diagramType);
				});
				menuButton.getItems().add(radioButton);
				if (networkView.getOptionDiagram().equals(diagramType))
					radioButton.setSelected(true);
			}
			{
				var checkMenuItem = BasicFX.copyMenu(List.of(controller.getFlipVerticallyMenuItem()), false).get(0);
				checkMenuItem.setText("");
				checkMenuItem.setGraphic(MaterialIcons.graphic(MaterialIcons.swap_vert));
				menuButton.getItems().addAll(new SeparatorMenuItem(), checkMenuItem);
			}

			networkView.optionDiagramProperty().addListener((v, o, n) -> {
				if (n != null)
					menuButton.setGraphic(n.icon());
			});
			if (networkView.getOptionDiagram() != null)
				menuButton.setGraphic(networkView.getOptionDiagram().icon());
		}

		controller.getCloseMenuItem().setOnAction(e -> {
			if (SaveBeforeClosingDialog.apply(window) != SaveBeforeClosingDialog.Result.cancel) {
				ProgramProperties.put("WindowGeometry", (new WindowGeometry(window.getStage())).toString());
				MainWindowManager.getInstance().closeMainWindow(window);
			}
		});

		var maintainAspectRatio = new SimpleBooleanProperty();
		maintainAspectRatio.bind(Bindings.createBooleanBinding(() -> networkView.getOptionDiagram().isRadialOrCircular(), networkView.optionDiagramProperty()));

		controller.getZoomInVerticallyMenuItem().setOnAction(e -> {
			if (maintainAspectRatio.get())
				scaleFactorX.set(1.1 * scaleFactorX.get());
			scaleFactorY.set(1.1 * scaleFactorY.get());
		});
		controller.getZoomInVerticallyMenuItem().disableProperty().bind(document.hasNetworksProperty().not());
		controller.getZoomOutVerticallyMenuItem().setOnAction(e -> {
			if (maintainAspectRatio.get())
				scaleFactorX.set(1 / 1.1 * scaleFactorX.get());
			scaleFactorY.set(1 / 1.1 * scaleFactorY.get());
		});
		controller.getZoomOutVerticallyMenuItem().disableProperty().bind(document.hasNetworksProperty().not());

		controller.getZoomToFitMenuItem().setOnAction(e -> {
			scaleFactorX.set(1.0);
			scaleFactorY.set(1.0);
			runUpdateNetworkDrawing();
		});


		controller.getZoomInHorizontallyMenuItem().setOnAction(e -> {
			scaleFactorX.set(1.1 * scaleFactorX.get());
			if (maintainAspectRatio.get())
				scaleFactorY.set(1.1 * scaleFactorY.get());
		});
		controller.getZoomInHorizontallyMenuItem().disableProperty().bind(controller.getZoomInVerticallyMenuItem().disableProperty().or(maintainAspectRatio));


		controller.getZoomOutHorizontallyMenuItem().setOnAction(e -> {
			scaleFactorX.set(1 / 1.1 * scaleFactorX.get());
			if (maintainAspectRatio.get())
				scaleFactorY.set(1 / 1.1 * scaleFactorY.get());
		});
		controller.getZoomOutHorizontallyMenuItem().disableProperty().bind(controller.getZoomInVerticallyMenuItem().disableProperty().or(maintainAspectRatio));

		scaleFactorX.addListener((v, o, n) -> {
			var group = BasicFX.getAllRecursively(networkView, Group.class).iterator().next();
			var scaleBy = n.doubleValue() / o.doubleValue();
			ScaleDrawing.apply(group, scaleBy, 1);
		});
		scaleFactorY.addListener((v, o, n) -> {
			var group = BasicFX.getAllRecursively(networkView, Group.class).iterator().next();
			var scaleBy = n.doubleValue() / o.doubleValue();
			ScaleDrawing.apply(group, 1, scaleBy);
		});

		networkView.optionFlipVerticallyProperty().addListener(e -> {
					networkView.applyVerticalFlip();
					runUpdateTreesDrawing();
				}
		);
		controller.getFlipVerticallyMenuItem().selectedProperty().bindBidirectional(networkView.optionFlipVerticallyProperty());
		controller.getFlipVerticallyMenuItem().disableProperty().bind(serviceRunning.or(Bindings.createBooleanBinding(() -> networkView.getOptionDiagram().isRadialOrCircular(), networkView.optionDiagramProperty())));
		networkView.optionDiagramProperty().addListener((v, o, n) -> {
			if (n != null && n.isRadialOrCircular())
				networkView.optionFlipVerticallyProperty().set(false);
		});

		controller.getIncreaseFontSizeMenuItem().setOnAction(e -> {
			for (var taxon : document.getTaxaBlock().getTaxa()) {
				if (window.getTaxaSelectionModel().size() == 0 || window.getTaxaSelectionModel().isSelected(taxon)) {
					var displayLabel = taxon.getDisplayLabel();
					var size = Math.min(128, 1.1 * RichTextLabel.getFontSize(displayLabel));
					displayLabel = RichTextLabel.setFontSize(displayLabel, size);
					taxon.setDisplayLabel(displayLabel);
				}
			}
		});
		controller.getDecreaseFontSizeMenuItem().setOnAction(e -> {
			for (var taxon : document.getTaxaBlock().getTaxa()) {
				if (window.getTaxaSelectionModel().size() == 0 || window.getTaxaSelectionModel().isSelected(taxon)) {
					var displayLabel = taxon.getDisplayLabel();
					var size = Math.max(4, 1 / 1.1 * RichTextLabel.getFontSize(displayLabel));
					displayLabel = RichTextLabel.setFontSize(displayLabel, size);
					taxon.setDisplayLabel(displayLabel);
				}
			}
		});
		controller.getIncreaseFontSizeMenuItem().disableProperty().bind(document.hasNetworksProperty().not());
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
		controller.getCopyImageMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(serviceRunning));

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
		controller.getExportImageMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(serviceRunning));

		controller.getExportNewickMenuItem().setOnAction(e -> ExportNewick.apply(window));
		controller.getExportNewickMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(serviceRunning));

		controller.getSaveMenuItem().setOnAction(e -> Save.showSaveDialog(this.window));
		controller.getSaveMenuItem().disableProperty().bind(document.emptyProperty().or(serviceRunning));

		controller.getUndoMenuItem().setOnAction(e -> {
			if (!(focusOwner.get() instanceof TextInputControl))
				undoManager.undo();
		});
		controller.getUndoMenuItem().textProperty().bind(undoManager.undoNameProperty());
		controller.getUndoMenuItem().disableProperty().bind(undoManager.undoableProperty().not().or(serviceRunning));

		controller.getRedoMenuItem().setOnAction(e -> {
			if (!(focusOwner.get() instanceof TextInputControl))
				undoManager.redo();
		});
		controller.getRedoMenuItem().textProperty().bind(undoManager.redoNameProperty());
		controller.getRedoMenuItem().disableProperty().bind(undoManager.redoableProperty().not().or(serviceRunning));

		controller.getCopyTaxaMenuItem().setOnAction(e -> {
			var taxa = (window.getTaxaSelectionModel().size() > 0 ? window.getTaxaSelectionModel().getSelectedItems() : document.getTaxaBlock().getTaxa());
			var network = document.getNetwork();
			var pairs = new ArrayList<Pair<Point2D, Taxon>>();
			for (var taxon : taxa) {
				var taxId = document.getTaxaBlock().indexOf(taxon);
				var v = network.nodeStream().filter(u -> network.hasTaxa(u) && network.getTaxon(u) == taxId).findAny().orElse(null);
				if (v != null) {
					var shape = networkView.getNodeLabeledNodeShapeMap().get(v);
					if (shape != null) {
						var pos = new Point2D(shape.getTranslateX(), shape.getTranslateY());
						pairs.add(new Pair<>(pos, taxon));
					}
				}
			}
			pairs.sort((a, b) -> {
				var compare = Double.compare(a.getKey().getY(), b.getKey().getY());
				return (compare == 0 ? Double.compare(a.getKey().getX(), b.getKey().getX()) : compare);
			});
			var names = pairs.stream().map(Pair::getValue).map(Taxon::getName).toList();
			ClipboardUtils.putString(StringUtils.toString(names, "\n"));
		});
		controller.getCopyTaxaMenuItem().disableProperty().bind(serviceRunning.or(document.hasNetworksProperty().not()));

		controller.getCopyTreesMenuItem().setOnAction(e -> {
			if (document.hasTrees()) {
				var trees = document.getTreeRecords().stream().filter(TreeRecord::isShow).map(TreeRecord::getTree).toList();
				if (!trees.isEmpty()) {
					try {
						ClipboardUtils.putString(ExportNewick.apply(trees));
					} catch (IOException ex) {
						System.err.println(ex.getMessage());
					}
				}
			}
		});
		controller.getCopyTreesMenuItem().disableProperty().bind(document.hasTreesProperty().not().or(serviceRunning));

		ExamplesSupport.install(controller.getExamplesFilesMenu(), (s, t) -> (new FileOpener()).accept(FileUtils.PREFIX_TO_INDICATE_TO_PARSE_FILENAME_STRING + t, s, null));

		controller.getPageSetupMenuItem().setOnAction(e -> jloda.fx.print.Print.showPageLayout(window.getStage()));
		controller.getPrintMenuItem().setOnAction((e) -> {
			var hPolicy = controller.getScrollPane().getHbarPolicy();
			var vPolicy = controller.getScrollPane().getVbarPolicy();
			try {
				controller.getScrollPane().setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
				controller.getScrollPane().setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
				jloda.fx.print.Print.print(window.getStage(), controller.getInnerAnchorPane());
			} finally {
				controller.getScrollPane().setHbarPolicy(hPolicy);
				controller.getScrollPane().setVbarPolicy(vPolicy);
			}
		});
		controller.getPrintMenuItem().disableProperty().bind(document.hasNetworksProperty().not());
		controller.getPageSetupMenuItem().setOnAction(e -> jloda.fx.print.Print.showPageLayout(window.getStage()));

		controller.getCopyNetworkMenuItem().setOnAction(e -> {
			if (document.hasNetworks()) {
				try {
					ClipboardUtils.putString(ExportNewick.apply(document.getNetwork()));
				} catch (IOException ex) {
					System.err.println(ex.getMessage());
				}
			}
		});
		controller.getCopyNetworkMenuItem().disableProperty().bind(document.hasNetworksProperty().not().or(serviceRunning));

		controller.getCopyMenuItem().setOnAction(e -> {
			if (focusOwner.get() instanceof TextInputControl tic) {
				tic.copy();              // native text-field copy, restored
			} else if (window.getTaxaSelectionModel().size() > 0) {
				controller.getCopyTaxaMenuItem().fire();
			} else if (controller.getScrollPane().isFocused() && document.hasNetworks())
				controller.getCopyNetworkMenuItem().fire();
			else if (document.hasTrees()) {
				controller.getCopyTreesMenuItem().fire();
			}
		});
		controller.getCopyMenuItem().disableProperty().bind(controller.getCopyTreesMenuItem().disableProperty().and(controller.getCopyNetworkMenuItem().disableProperty()));


		var canEditTreesList = new SimpleBooleanProperty(this, "canEditTreesList", false);
		canEditTreesList.bind(document.hasTreesProperty().and(document.hasNetworksProperty().not()).or(serviceRunning.not()));

		controller.getPasteMenuItem().setOnAction(e -> {
			if (focusOwner.get() instanceof TextInputControl) {
				// no need for any action
			} else if (ClipboardUtils.hasString()) {
				try {
					ImportNewick.apply(window, new BufferedReader(new StringReader(ClipboardUtils.getString())), true);
				} catch (IOException ex) {
					WindowNotifications.showWarning(controller.getCenterPane(), "Paste failed: " + ex.getMessage());
				}
			}
		});
		controller.getPasteMenuItem().disableProperty().bind(canEditTreesList.not().and(document.emptyProperty().not()));

		controller.getClearMenuItem().setOnAction(e -> {
			if (focusOwner.get() instanceof TextInputControl tic)
				tic.clear();
			else {
				networkView.clear();
				document.getNetworks().clear();
			}
		});
		controller.getClearMenuItem().disableProperty().bind((document.hasTreesProperty().and(document.hasNetworksProperty()).not()));


		controller.getImportTreeNamesMenuItem().setOnAction(e -> {
			var previousFile = new File(jloda.util.ProgramProperties.get("TreeNamesFile", ""));

			var fileChooser = new FileChooser();
			if (FileUtils.fileExistsAndIsNonEmpty(previousFile)) {
				fileChooser.setInitialDirectory(previousFile.getParentFile());
				fileChooser.setInitialFileName(previousFile.getName());
			}
			if (jloda.util.ProgramProperties.getProgramVersion() != null)
				fileChooser.setTitle("Open File - " + jloda.util.ProgramProperties.getProgramVersion());
			else
				fileChooser.setTitle("Open File");
			fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("text", "*.txt"));
			final File selectedFile = fileChooser.showOpenDialog(window.getStage());

			if (selectedFile != null) {
				jloda.util.ProgramProperties.put("TreeNamesFile", selectedFile);
				try {
					String separator = null;
					for (var line : FileUtils.getLinesFromFile(selectedFile.getPath())) {
						if (!line.startsWith("#")) {
							if (separator == null) {
								if (line.contains("\t"))
									separator = "\t";
								else if (line.contains(";"))
									separator = ";";
								else if (line.contains(","))
									separator = ",";
								else throw new IOException("File must be tab-, comma- or semi-colon separated");
							}
							var tokens = line.split(separator);
							if (tokens.length == 2 && NumberUtils.isInteger(tokens[0])) {
								var id = NumberUtils.parseInt(tokens[0]);
								var name = tokens[1];
								document.getTreeRecords().stream().filter(r -> r.getId() == id).findFirst().ifPresent(record -> record.setName(name));
							}
						}
					}
					if (separator != null) {
						runUpdateTreesDrawing();
					}
				} catch (IOException ex) {
					WindowNotifications.showError(controller.getCenterPane(), ex.getMessage());
				}
			}
		});
		controller.getImportTreeNamesMenuItem().disableProperty().bind(canEditTreesList.not());

		var updaterService = UpdateService.get();
		controller.getCheckForUpdatesMenuItem().setOnAction(e -> updaterService.checkForUpdates(window.getStage(), Version.HOME_URL, Version.NAME, Version.VERSION));
		controller.getCheckForUpdatesMenuItem().disableProperty().bind(updaterService.disabledProperty().or(MainWindowManager.getInstance().sizeProperty().greaterThan(1)).or(window.dirtyProperty()));

		controller.getShowTreesExhaustive().setOnAction(e -> {
			for (var network : document.getNetworks()) {
				TreeTrace.clearTT(network);
			}
			runUpdateTreesDrawing();
		});

		controller.getOpenOnlineUserManualInBrowserMenuItem().setOnAction(e -> ProgramProperties.getHostServices().showDocument(Version.WEBSITE_URL));

		controller.getSetWindowSizeMenuItem().setOnAction(e -> {
			var result = SetParameterDialog.apply(window.getStage(), "Enter size (width x height)",
					"%.0f x %.0f".formatted(window.getStage().getWidth(), window.getStage().getHeight()));

			if (result != null) {
				var tokens = StringUtils.split(result, 'x');
				if (tokens.length == 2 && NumberUtils.isInteger(tokens[0]) && NumberUtils.isInteger(tokens[1])) {
					var width = Math.max(50, NumberUtils.parseDouble(tokens[0]));
					var height = Math.max(50, NumberUtils.parseDouble(tokens[1]));
					window.getStage().setWidth(width);
					window.getStage().setHeight(height);
				}
			}
		});

		SetupFind.apply(window);

		SetupDropTarget.apply(controller.getScrollPane(), FileOpener::isAcceptable, files -> {
					if (files.size() <= 10)
						ImportNewick.apply(window, files);
					else new ConfirmInternalDialog(controller.getCenterAnchorPane(), "Import many trees",
							"Do you really want to import %,d files?".formatted(files.size()),
							() -> ImportNewick.apply(window, files)).show();
				},
				s -> s.toLowerCase().startsWith("#nexus") || s.startsWith("(") && s.contains(")"),
				s -> {
					try {
						ImportNewick.apply(window, new BufferedReader(new StringReader(s)), true);
					} catch (IOException ignored) {
					}
				});

		SetupRubberBandSelection.apply(document, networkView, window.getTaxaSelectionModel().getSelectedItems());

		var taxonPane = new TaxonLabelFormat(window.getTaxaSelectionModel(), window.dirtyProperty(), window.getUndoManager());
		controller.getTaxonLabelsTitledPane().setContent(taxonPane.getController().getTitledPane().getContent());

		// PhyloFusion settings pane: expose the layout algorithm's options to advanced users (bound to the document's
		// PhyloFusion instance, which AlgorithmsService uses to compute the layout)
		var algorithm = document.getLayoutAlgorithm();
		var reticulationCBox = controller.getReticulationPlacementCBox();
		reticulationCBox.getItems().setAll(PhyloFusionAlgorithm.ReticulationPreference.values());
		reticulationCBox.valueProperty().bindBidirectional(algorithm.optionReticulatePlacementProperty());
		var edgeWeightsCBox = controller.getEdgeWeightsCBox();
		edgeWeightsCBox.getItems().setAll(PhyloFusion.EdgeWeights.values());
		edgeWeightsCBox.valueProperty().bindBidirectional(algorithm.optionEdgeWeightsProperty());
		controller.getMutualRefinementCheckBox().selectedProperty().bindBidirectional(algorithm.optionMutualRefinementProperty());
		controller.getGroupNonSeparatedCheckBox().selectedProperty().bindBidirectional(algorithm.optionGroupNonSeparatedProperty());
		controller.getMissingTaxaHeuristicCheckBox().selectedProperty().bindBidirectional(algorithm.optionMissingTaxaHeuristicProperty());
		// open on the taxon-labels pane, matching the previous behaviour of the Format button
		controller.getSettingsAccordion().setExpandedPane(controller.getTaxonLabelsTitledPane());
		controller.getRemoveTaxaMenuItem().setOnAction(e -> {
			removeTaxaService.setupCalculation(window, window.getTaxaSelectionModel().getSelectedItems());
			removeTaxaService.restart();
		});
		controller.getRemoveTaxaMenuItem().disableProperty().bind(serviceRunning.or(window.getTaxaSelectionModel().sizeProperty().isEqualTo(0))
				.or(window.getTaxaSelectionModel().sizeProperty().greaterThanOrEqualTo(document.numberOfTaxaProperty().subtract(4))));
	}

	public static Collection<TreeRecord> getSelectedRowsOrAll(TableView<TreeRecord> treeTableView, List<TreeRecord> treeRecords) {
		if (treeTableView.getSelectionModel().getSelectedItems().isEmpty()) {
			return treeRecords;
		} else {
			return treeTableView.getSelectionModel().getSelectedItems();
		}
	}

	/**
	 * wires the dataset note: the text area and the document's note property are kept in sync in both
	 * directions, user edits mark the window dirty, and a note present on load is revealed automatically
	 * so its provenance is visible
	 */
	private void setupNote(Document document) {
		var noteTextArea = controller.getNoteTextArea();
		var toggle = controller.getNoteToggleButton();
		toggle.setSelected(false);
		var updating = new boolean[]{false}; // guards against the two listeners echoing each other

		// user edits flow to the document and mark the window dirty
		noteTextArea.textProperty().addListener((v, o, n) -> {
			if (!updating[0]) {
				document.setNote(n);
				window.dirtyProperty().set(true);
			}
		});

		// programmatic changes to the note (e.g. on load) flow back to the text area, without marking dirty
		document.noteProperty().addListener((v, o, n) -> {
			if (!noteTextArea.getText().equals(n)) {
				updating[0] = true;
				noteTextArea.setText(n == null ? "" : n);
				updating[0] = false;
			}
			if (false && n != null && !n.isBlank()) // reveal a loaded note so the user sees where the data came from
				toggle.setSelected(true);
		});

		// initialize from whatever the document already holds
		noteTextArea.setText(document.getNote());
		if (false) toggle.setSelected(!document.getNote().isBlank());
	}

	/**
	 * schedules a recomputation of the network, restarting the delay each time this is called, so that
	 * repeatedly pressing a spinner arrow, or holding it down, leads to only one recomputation, once
	 * the user has stopped
	 */
	public void runRecomputeNetworkAfterAWhile() {
		RunAfterAWhile.apply("runRecomputeNetwork", () -> Platform.runLater(this::runRecomputeNetwork), RECOMPUTE_DELAY);
	}

	public void runRecomputeNetwork() {
		algorithmsService.setupCalculation(this.window, true);
		algorithmsService.setOnScheduled(a -> {
			networkView.clear();
		});
		algorithmsService.setOnSucceeded(a -> {
			runUpdateNetworkDrawing();
			window.dirtyProperty().set(true);
		});
		algorithmsService.restart();
	}

	public void runUpdateNetworkDrawing() {
		updateNetworkDrawing.run();
	}

	public void runUpdateTreesDrawing() {
		updateTreesDrawing.run();

		if (!algorithmsService.isRunning() && BruteForceTreeTracer.requireTracing(window.getDocument().getNetworks(), window.getDocument().getTreeRecords())) {
			algorithmsService.setupCalculation(window, false);
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

	/**
	 * all options of this window that are to be saved and restored, and that drive the
	 * tooltips and ranges of the associated controls
	 */
	public OptionsRegistry getOptionsRegistry() {
		return optionsRegistry;
	}
}
