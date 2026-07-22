/*
 * FileOpener.java Copyright (C) 2026 Daniel H. Huson
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

package phyloparallelograms.io;

import javafx.application.Platform;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.MainWindowManager;
import jloda.fx.windownotifications.WindowNotifications;
import jloda.phylo.PhyloGraph;
import jloda.util.FileUtils;
import phyloparallelograms.window.MainWindow;
import phyloparallelograms.window.NewWindow;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.NexusImporter;
import splitstree6.io.writers.trees.NewickWriter;

import java.io.*;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * opens a file
 * Daniel Huson, 9.2024
 */
public class FileOpener implements Consumer<String> {

	@Override
	public void accept(String fileName) {
		Platform.runLater(() -> accept(fileName, fileName, null));
	}

	public void accept(String fileName, String title, MainWindow window) {
		if (window == null) {
			window = (MainWindow) MainWindowManager.getInstance().getLastFocusedMainWindow();
			if (window == null || !window.isEmpty()) {
				window = NewWindow.apply();
			}
		}
		try {
			if (PhyloParallelogramsText.isPhyloParallelogramsTextFile(fileName)) {
				PhyloParallelogramsText.load(fileName, window.getDocument(), window.getOptionsRegistry());
			} else if (SQLiteUtils.isSQLiteWithTreesOrNetworksTable(fileName)) {
				// legacy: PhyloParallelograms files written in the old SQLite format (desktop only; the native
				// driver is absent on iOS, where isSQLiteWithTreesOrNetworksTable simply returns false)
				PhyloParallelogramsDB.load(fileName, window.getDocument(), window.getOptionsRegistry());
			} else {
				var firstLine = Objects.requireNonNull(FileUtils.getFirstLineFromFile(new File(fileName))).trim().toLowerCase();
				if (firstLine.startsWith("#nexus")) {
					var taxa = new TaxaBlock();
					var trees = new TreesBlock();
					NexusImporter.parse(fileName, taxa, trees);

					var w = new StringWriter();
					var newickWriter = new NewickWriter();
					newickWriter.optionEdgeWeightsProperty().set(trees.getTrees().stream().anyMatch(PhyloGraph::hasEdgeWeights));
					newickWriter.optionEdgeConfidencesProperty().set(trees.getTrees().stream().anyMatch(PhyloGraph::hasEdgeConfidences));
					newickWriter.write(w, taxa, trees);
					ImportNewick.apply(window, new BufferedReader(new StringReader(w.toString())), true);
				} else if (firstLine.startsWith("<nex:nexml") || firstLine.startsWith("<?xml version="))
					WindowNotifications.showWarning(window.getController().getCenterAnchorPane(), "NEXML: not implemented");
				else if (firstLine.startsWith("(") && firstLine.contains(")")) {
					ImportNewick.apply(window, fileName);
				}
			}
			if (!fileName.endsWith(".phycmp") && fileName.equals(title)) // forget old PhyloCompare files and only if fileName==title
				RecentFilesManager.getInstance().insertRecentFile(fileName);
			window.setFileName(title);
			WindowNotifications.showInfo(window.getController().getCenterPane(), "Loaded file: " + FileUtils.getFileNameWithoutPath(title));

		} catch (IOException e) {
			WindowNotifications.showError(window.getController().getCenterPane(), "Open file failed: " + e.getMessage());
		}
	}

	public static boolean isAcceptable(File file) {
		var fileName = file.getPath();
		if (PhyloParallelogramsText.isPhyloParallelogramsTextFile(fileName))
			return true;
		if (SQLiteUtils.isSQLiteWithTreesOrNetworksTable(fileName))
			return true;
		var firstLine = Objects.requireNonNull(FileUtils.getFirstLineFromFile(new File(fileName))).trim().toLowerCase();
		return (firstLine.startsWith("#nexus") || (firstLine.startsWith("(") && firstLine.contains(")")));

	}
}