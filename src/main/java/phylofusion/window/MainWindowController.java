

/*
 * MainWindowController.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableUtils;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.MainWindowManager;
import phylofusion.utils.BulkHeaderCheckBox;

import java.util.List;

public class MainWindowController {
	@FXML
	private AnchorPane rootPane;

	@FXML
	private MenuItem aboutMenuItem;

	@FXML
	private AnchorPane bottomAnchorPane;

	@FXML
	private FlowPane bottomFlowPane;

	@FXML
	private AnchorPane centerAnchorPane;

	@FXML
	private StackPane centerPane;

	@FXML
	private MenuItem checkForUpdatesMenuItem;

	@FXML
	private RadioMenuItem rectangularCladogramMenuItem;

	@FXML
	private RadioMenuItem circularCladogramMenuItem;

	@FXML
	private RadioMenuItem radialCladogramMenuItem;

	@FXML
	private MenuItem clearMenuItem;

	@FXML
	private MenuItem closeMenuItem;

	@FXML
	private MenuItem copyImageMenuItem;

	@FXML
	private MenuItem copyMenuItem;

	@FXML
	private MenuItem copyTreesMenuItem;

	@FXML
	private MenuItem copyNetworkMenuItem;

	@FXML
	private MenuItem cutMenuItem;

	@FXML
	private MenuItem decreaseFontSizeMenuItem;

	@FXML
	private MenuItem deleteMenuItem;

	@FXML
	private Menu editMenu;

	@FXML
	private Menu exportMenu;

	@FXML
	private MenuItem exportNewickMenuItem;

	@FXML
	private MenuItem exportImageMenuItem;

	@FXML
	private Menu fileMenu;

	@FXML
	private MenuItem findAgainMenuItem;

	@FXML
	private MenuItem findMenuItem;

	@FXML
	private MenuItem fullScreenMenuItem;

	@FXML
	private MenuItem increaseFontSizeMenuItem;

	@FXML
	private Label memoryUsageLabel;

	@FXML
	private MenuBar menuBar;

	@FXML
	private MenuItem newMenuItem;

	@FXML
	private MenuItem openMenuItem;

	@FXML
	private MenuItem pageSetupMenuItem;

	@FXML
	private MenuItem pasteMenuItem;

	@FXML
	private MenuItem printMenuItem;

	@FXML
	private MenuItem quitMenuItem;

	@FXML
	private Menu recentFilesMenu;

	@FXML
	private MenuItem redoMenuItem;

	@FXML
	private Button runButton;

	@FXML
	private MenuItem runMenuItem;

	@FXML
	private MenuItem saveMenuItem;

	@FXML
	private MenuItem setWindowSizeMenuItem;

	@FXML
	private CheckMenuItem showHelpWindow;

	@FXML
	private TableView<TreeRecord> treeTable;

	@FXML
	private TableColumn<TreeRecord, String> treeColumn;

	@FXML
	private TableColumn<TreeRecord, Boolean> runColumn;

	@FXML
	private TableColumn<TreeRecord, Boolean> showColumn;

	@FXML
	private Menu treesMenu;

	@FXML
	private MenuItem useAllMenuItem;

	@FXML
	private MenuItem useNoneMenuItem;

	@FXML
	private MenuItem setConfidenceThresholdMenuItem;

	@FXML
	private MenuItem showAllMenuItem;

	@FXML
	private MenuItem showNoneMenuItem;

	@FXML
	private MenuItem showSelectedMenuItem;

	@FXML
	private MenuItem undoMenuItem;

	@FXML
	private CheckMenuItem useDarkThemeCheckMenuItem;

	@FXML
	private Menu windowMenu;

	@FXML
	private MenuItem zoomInMenuItem;

	@FXML
	private MenuItem zoomOutMenuItem;

	@FXML
	private MenuItem zoomToFitMenuItem;

	@FXML
	private TextField confidenceTextField;

	@FXML
	private Label statusLabel;

	@FXML
	private SplitPane splitPane;

	@FXML
	private Button selectAllTableButton;

	@FXML
	private Button selectNoneTableButton;

	@FXML
	private MenuButton diagramMenuButton;

	@FXML
	private Spinner<Double> outlineWidthSpinner;

	@FXML
	private Button showButton;

	@FXML
	private VBox legendVBox;

	@FXML
	private MenuButton exportMenuButton;

	@FXML
	private Button zoomInButton;

	@FXML
	private Button zoomOutButton;

	@FXML
	private AnchorPane innerAnchorPane;

	@FXML
	private CheckBox outlineCBox;

	@FXML
	private CheckMenuItem reticulateEdgesAreSpecialCheckMenuItem;

	private ZoomableScrollPane scrollPane;

	private final BooleanProperty disableAllShow = new SimpleBooleanProperty(false);
	private final BooleanProperty disableAllRun = new SimpleBooleanProperty(false);

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(runButton, MaterialIcons.play_circle, "", false);
		MaterialIcons.setIcon(showButton, MaterialIcons.play_circle, "", false);
		MaterialIcons.setIcon(selectAllTableButton, MaterialIcons.select_all);
		MaterialIcons.setIcon(selectNoneTableButton, MaterialIcons.deselect);
		MaterialIcons.setIcon(exportMenuButton, MaterialIcons.ios_share);
		MaterialIcons.setIcon(zoomInButton, MaterialIcons.zoom_in);
		MaterialIcons.setIcon(zoomOutButton, MaterialIcons.zoom_out);

		if (ProgramProperties.isMacOS()) {
			getMenuBar().setUseSystemMenuBar(true);
			fileMenu.getItems().remove(getQuitMenuItem());
			// windowMenu.getItems().remove(getAboutMenuItem());
			//editMenu.getItems().remove(getPreferencesMenuItem());
		}

		confidenceTextField.setTextFormatter(new TextFormatter<>(change ->
				change.getControlNewText().matches("-?\\d*(\\.\\d*)?") ? change : null));
		confidenceTextField.setText("0.0");

		statusLabel.setText("");

		scrollPane = new ZoomableScrollPane(new Pane());
		scrollPane.setFitToWidth(true);
		scrollPane.setFitToHeight(true);
		scrollPane.setPannable(true);
		scrollPane.setLockAspectRatio(true);
		scrollPane.setRequireShiftOrControlToZoom(true);

		centerPane.getChildren().add(scrollPane);

		TableViewSupport.apply(treeTable, treeColumn, runColumn, showColumn, disableAllRun, disableAllShow, this);

		var runBulkHeaderCheckBox = new BulkHeaderCheckBox<>(treeTable, TreeRecord::runProperty).getNode();
		disableAllRun.addListener((v, o, n) -> {
			if (n)
				runColumn.setGraphic(null);
			else
				runColumn.setGraphic(runBulkHeaderCheckBox);
		});
		var showBulkHeaderCheckBox = new BulkHeaderCheckBox<>(treeTable, TreeRecord::showProperty).getNode();
		disableAllShow.addListener((v, o, n) -> {
			if (n)
				showColumn.setGraphic(null);
			else
				showColumn.setGraphic(showBulkHeaderCheckBox);
		});
		var tip = new javafx.scene.control.Tooltip("Applies to selected rows; if none selected, applies to all rows.");
		Tooltip.install(runColumn.getGraphic(), tip);
		Tooltip.install(showColumn.getGraphic(), tip);

		zoomInButton.setOnAction(e -> zoomInMenuItem.fire());
		zoomInButton.disableProperty().bind(zoomInMenuItem.disableProperty());
		zoomOutButton.setOnAction(e -> zoomOutMenuItem.fire());
		zoomOutButton.disableProperty().bind(zoomOutMenuItem.disableProperty());

		DraggableUtils.makeDraggableInAnchorPane(legendVBox);

		exportMenuButton.getItems().addAll(BasicFX.copyMenu(List.of(copyImageMenuItem), false));

		runButton.onActionProperty().bindBidirectional(runMenuItem.onActionProperty());
		runButton.disableProperty().bindBidirectional(runMenuItem.disableProperty());

		ProgramProperties.track(outlineCBox.selectedProperty(), true);

		MainWindowManager.useDarkThemeProperty().addListener((v, o, n) -> {
			if (n)
				scrollPane.setStyle("-fx-background: black;-fx-background-color: black;");
			else
				scrollPane.setStyle("-fx-background: white;-fx-background-color: white;");
		});
		if (MainWindowManager.useDarkThemeProperty().get())
			scrollPane.setStyle("-fx-background: black;-fx-background-color: black;");
		else
			scrollPane.setStyle("-fx-background: white;-fx-background-color: white;");

	}

	public BooleanProperty disableAllRunProperty() {
		return disableAllRun;
	}

	public BooleanProperty disableAllShowProperty() {
		return disableAllShow;
	}

	public MenuItem getAboutMenuItem() {
		return aboutMenuItem;
	}

	public AnchorPane getBottomAnchorPane() {
		return bottomAnchorPane;
	}

	public FlowPane getBottomFlowPane() {
		return bottomFlowPane;
	}

	public AnchorPane getCenterAnchorPane() {
		return centerAnchorPane;
	}

	public StackPane getCenterPane() {
		return centerPane;
	}

	public MenuItem getCheckForUpdatesMenuItem() {
		return checkForUpdatesMenuItem;
	}


	public MenuItem getClearMenuItem() {
		return clearMenuItem;
	}

	public MenuItem getCloseMenuItem() {
		return closeMenuItem;
	}

	public MenuItem getCopyImageMenuItem() {
		return copyImageMenuItem;
	}

	public MenuItem getCopyMenuItem() {
		return copyMenuItem;
	}

	public MenuItem getCutMenuItem() {
		return cutMenuItem;
	}

	public MenuItem getDecreaseFontSizeMenuItem() {
		return decreaseFontSizeMenuItem;
	}

	public MenuItem getDeleteMenuItem() {
		return deleteMenuItem;
	}

	public Menu getEditMenu() {
		return editMenu;
	}

	public Menu getExportMenu() {
		return exportMenu;
	}

	public MenuItem getExportNewickMenuItem() {
		return exportNewickMenuItem;
	}

	public MenuItem getExportImageMenuItem() {
		return exportImageMenuItem;
	}

	public Menu getFileMenu() {
		return fileMenu;
	}

	public MenuItem getFindAgainMenuItem() {
		return findAgainMenuItem;
	}

	public MenuItem getFindMenuItem() {
		return findMenuItem;
	}

	public MenuItem getFullScreenMenuItem() {
		return fullScreenMenuItem;
	}

	public MenuItem getIncreaseFontSizeMenuItem() {
		return increaseFontSizeMenuItem;
	}

	public Label getMemoryUsageLabel() {
		return memoryUsageLabel;
	}

	public MenuBar getMenuBar() {
		return menuBar;
	}

	public MenuItem getNewMenuItem() {
		return newMenuItem;
	}

	public MenuItem getOpenMenuItem() {
		return openMenuItem;
	}

	public MenuItem getPageSetupMenuItem() {
		return pageSetupMenuItem;
	}

	public MenuItem getPasteMenuItem() {
		return pasteMenuItem;
	}


	public MenuItem getPrintMenuItem() {
		return printMenuItem;
	}

	public MenuItem getQuitMenuItem() {
		return quitMenuItem;
	}

	public Menu getRecentFilesMenu() {
		return recentFilesMenu;
	}

	public MenuItem getRedoMenuItem() {
		return redoMenuItem;
	}

	public MenuItem getRunMenuItem() {
		return runMenuItem;
	}

	public MenuItem getSaveMenuItem() {
		return saveMenuItem;
	}

	public MenuItem getSetWindowSizeMenuItem() {
		return setWindowSizeMenuItem;
	}

	public CheckMenuItem getShowHelpWindow() {
		return showHelpWindow;
	}

	public TableView<TreeRecord> getTreeTable() {
		return treeTable;
	}

	public TableColumn<TreeRecord, String> getTreeColumn() {
		return treeColumn;
	}

	public TableColumn<TreeRecord, Boolean> getRunColumn() {
		return runColumn;
	}

	public TableColumn<TreeRecord, Boolean> getShowColumn() {
		return showColumn;
	}

	public Menu getTreesMenu() {
		return treesMenu;
	}

	public MenuItem getUndoMenuItem() {
		return undoMenuItem;
	}

	public CheckMenuItem getUseDarkThemeCheckMenuItem() {
		return useDarkThemeCheckMenuItem;
	}

	public Menu getWindowMenu() {
		return windowMenu;
	}

	public MenuItem getZoomInMenuItem() {
		return zoomInMenuItem;
	}

	public MenuItem getZoomOutMenuItem() {
		return zoomOutMenuItem;
	}

	public MenuItem getZoomToFitMenuItem() {
		return zoomToFitMenuItem;
	}

	public MenuItem getUseAllMenuItem() {
		return useAllMenuItem;
	}

	public MenuItem getUseNoneMenuItem() {
		return useNoneMenuItem;
	}

	public MenuItem getShowAllMenuItem() {
		return showAllMenuItem;
	}

	public MenuItem getShowNoneMenuItem() {
		return showNoneMenuItem;
	}

	public MenuItem getShowSelectedMenuItem() {
		return showSelectedMenuItem;
	}

	public TextField getConfidenceTextField() {
		return confidenceTextField;
	}

	public Label getStatusLabel() {
		return statusLabel;
	}

	public MenuItem getSetConfidenceThresholdMenuItem() {
		return setConfidenceThresholdMenuItem;
	}

	public ZoomableScrollPane getScrollPane() {
		return scrollPane;
	}

	public SplitPane getSplitPane() {
		return splitPane;
	}

	public AnchorPane getRootPane() {
		return rootPane;
	}

	public Button getSelectAllTableButton() {
		return selectAllTableButton;
	}

	public Button getSelectNoneTableButton() {
		return selectNoneTableButton;
	}

	public Spinner<Double> getOutlineWidthSpinner() {
		return outlineWidthSpinner;
	}

	public MenuButton getDiagramMenuButton() {
		return diagramMenuButton;
	}

	public RadioMenuItem getRectangularCladogramMenuItem() {
		return rectangularCladogramMenuItem;
	}

	public RadioMenuItem getCircularCladogramMenuItem() {
		return circularCladogramMenuItem;
	}

	public RadioMenuItem getRadialCladogramMenuItem() {
		return radialCladogramMenuItem;
	}

	public VBox getLegendVBox() {
		return legendVBox;
	}

	public MenuButton getExportMenuButton() {
		return exportMenuButton;
	}

	public AnchorPane getInnerAnchorPane() {
		return innerAnchorPane;
	}

	public CheckBox getOutlineCBox() {
		return outlineCBox;
	}

	public Button getShowButton() {
		return showButton;
	}

	public CheckMenuItem getReticulateEdgesAreSpecialCheckMenuItem() {
		return reticulateEdgesAreSpecialCheckMenuItem;
	}

	public MenuItem getCopyTreesMenuItem() {
		return copyTreesMenuItem;
	}

	public MenuItem getCopyNetworkMenuItem() {
		return copyNetworkMenuItem;
	}
}
