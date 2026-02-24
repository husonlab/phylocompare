/*
 *  Copyright (C) 2018. Daniel H. Huson
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
 */

package phylofusion.window;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import jloda.fx.util.ProgramProperties;

public class MainWindowController {

	@FXML
	private MenuItem aboutMenuItem;

	@FXML
	private MenuItem addLSAEdgeMenuItem;

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
	private RadioMenuItem circularLayoutMenuItem;

	@FXML
	private RadioMenuItem cladogramEarlyMenuItem;

	@FXML
	private RadioMenuItem cladogramLateMenuItem;

	@FXML
	private MenuItem clearMenuItem;

	@FXML
	private MenuItem closeMenuItem;

	@FXML
	private MenuItem copyImageMenuItem;

	@FXML
	private MenuItem copyMenuItem;

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
	private Menu fileMenu;

	@FXML
	private MenuItem findAgainMenuItem;

	@FXML
	private MenuItem findMenuItem;

	@FXML
	private MenuItem flipHorizontalMenuItem;

	@FXML
	private MenuItem flipVerticalMenuItem;

	@FXML
	private MenuItem fullScreenMenuItem;

	@FXML
	private MenuItem increaseFontSizeMenuItem;

	@FXML
	private MenuItem layoutLabelMenuItem;

	@FXML
	private Menu layoutMenu;

	@FXML
	private MenuItem layoutPhylogenyMenuItem;

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
	private RadioMenuItem phylogramMenuItem;

	@FXML
	private MenuItem printMenuItem;

	@FXML
	private MenuItem quitMenuItem;

	@FXML
	private RadioMenuItem radialLayoutMenuItem;

	@FXML
	private Menu recentFilesMenu;

	@FXML
	private RadioMenuItem rectangularLayoutMenuItem;

	@FXML
	private MenuItem redoMenuItem;

	@FXML
	private CheckMenuItem resizeModeCheckMenuItem;

	@FXML
	private MenuItem rotateLeftMenuItem;

	@FXML
	private MenuItem rotateRightMenuItem;

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
	private TableView<TreeRow> treeTable;

	@FXML
	private TableColumn<TreeRow, String> treeColumn;

	@FXML
	private TableColumn<TreeRow, Boolean> useColumn;

	@FXML
	private TableColumn<TreeRow, Boolean> showColumn;

	@FXML
	private Menu treesMenu;

	@FXML
	private MenuItem useAllMenuItem;

	@FXML
	private MenuItem useNoneMenuItem;

	@FXML
	private MenuItem showAllMenuItem;

	@FXML
	private MenuItem showNoneMenuItem;


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
	private Label confidenceLabel;

	private final BooleanProperty disableAllShow = new SimpleBooleanProperty(false);

	@FXML
	private void initialize() {
		if (ProgramProperties.isMacOS()) {
			getMenuBar().setUseSystemMenuBar(true);
			fileMenu.getItems().remove(getQuitMenuItem());
			// windowMenu.getItems().remove(getAboutMenuItem());
			//editMenu.getItems().remove(getPreferencesMenuItem());
		}

		confidenceLabel.setText("");
		confidenceTextField.setTextFormatter(new TextFormatter<>(change ->
				change.getControlNewText().matches("-?\\d*(\\.\\d*)?") ? change : null));
		confidenceTextField.setText("0.0");
		TableViewSupport.apply(treeTable, treeColumn, useColumn, showColumn, disableAllShow, this);

		runButton.setOnAction(e -> runMenuItem.fire());
		runButton.disableProperty().bind(runMenuItem.disableProperty());

	}

	public BooleanProperty disableAllShowProperty() {
		return disableAllShow;
	}

	public MenuItem getAboutMenuItem() {
		return aboutMenuItem;
	}

	public MenuItem getAddLSAEdgeMenuItem() {
		return addLSAEdgeMenuItem;
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

	public RadioMenuItem getCircularLayoutMenuItem() {
		return circularLayoutMenuItem;
	}

	public RadioMenuItem getCladogramEarlyMenuItem() {
		return cladogramEarlyMenuItem;
	}

	public RadioMenuItem getCladogramLateMenuItem() {
		return cladogramLateMenuItem;
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

	public Menu getFileMenu() {
		return fileMenu;
	}

	public MenuItem getFindAgainMenuItem() {
		return findAgainMenuItem;
	}

	public MenuItem getFindMenuItem() {
		return findMenuItem;
	}

	public MenuItem getFlipHorizontalMenuItem() {
		return flipHorizontalMenuItem;
	}

	public MenuItem getFlipVerticalMenuItem() {
		return flipVerticalMenuItem;
	}

	public MenuItem getFullScreenMenuItem() {
		return fullScreenMenuItem;
	}

	public MenuItem getIncreaseFontSizeMenuItem() {
		return increaseFontSizeMenuItem;
	}

	public MenuItem getLayoutLabelMenuItem() {
		return layoutLabelMenuItem;
	}

	public Menu getLayoutMenu() {
		return layoutMenu;
	}

	public MenuItem getLayoutPhylogenyMenuItem() {
		return layoutPhylogenyMenuItem;
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

	public RadioMenuItem getPhylogramMenuItem() {
		return phylogramMenuItem;
	}

	public MenuItem getPrintMenuItem() {
		return printMenuItem;
	}

	public MenuItem getQuitMenuItem() {
		return quitMenuItem;
	}

	public RadioMenuItem getRadialLayoutMenuItem() {
		return radialLayoutMenuItem;
	}

	public Menu getRecentFilesMenu() {
		return recentFilesMenu;
	}

	public RadioMenuItem getRectangularLayoutMenuItem() {
		return rectangularLayoutMenuItem;
	}

	public MenuItem getRedoMenuItem() {
		return redoMenuItem;
	}

	public CheckMenuItem getResizeModeCheckMenuItem() {
		return resizeModeCheckMenuItem;
	}

	public MenuItem getRotateLeftMenuItem() {
		return rotateLeftMenuItem;
	}

	public MenuItem getRotateRightMenuItem() {
		return rotateRightMenuItem;
	}

	public Button getRunButton() {
		return runButton;
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

	public TableView<TreeRow> getTreeTable() {
		return treeTable;
	}

	public TableColumn<TreeRow, String> getTreeColumn() {
		return treeColumn;
	}

	public TableColumn<TreeRow, Boolean> getUseColumn() {
		return useColumn;
	}

	public TableColumn<TreeRow, Boolean> getShowColumn() {
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

	public TextField getConfidenceTextField() {
		return confidenceTextField;
	}

	public Label getConfidenceLabel() {
		return confidenceLabel;
	}
}
