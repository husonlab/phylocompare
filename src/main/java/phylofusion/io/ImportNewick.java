/*
 * ImportNewick.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.io;

import javafx.application.Platform;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.windownotifications.WindowNotifications;
import jloda.phylo.CommentData;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.FileUtils;
import jloda.util.StringUtils;
import phylofusion.window.MainWindow;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * newick import
 * Daniel Huson, 8.2024
 */
public class ImportNewick {
	/**
	 * import from file
	 *
	 * @param fileName file
	 * @param window   the window to import into
	 * @throws IOException
	 */
	public static void apply(MainWindow window, String fileName) throws IOException {
		try (var r = new BufferedReader(FileUtils.getReaderPossiblyZIPorGZIP(fileName))) {
			apply(r, window);
		}
	}

	/**
	 * import from a buffered reader
	 *
	 * @param r      reader
	 * @param window window
	 * @throws IOException
	 */
	public static void apply(BufferedReader r, MainWindow window) throws IOException {
		var phylogenies = apply(r);
		var areTrees = phylogenies.stream().noneMatch(t -> t.nodeStream().anyMatch(v -> v.getInDegree() > 1));
		var document = window.getDocument();

		var count = 0;
		if (areTrees) {
			if (!document.hasTrees())
				document.clear();
			var names = new ArrayList<String>();
			for (var tree : phylogenies) {
				if (tree.getName() == null || tree.getName().isBlank()) {
					tree.setName("T%03d".formatted(++count));
				}
				tree.setName(StringUtils.getUniqueName(tree.getName(), names));
				names.add(tree.getName());
			}
			document.addTrees(phylogenies);
			Platform.runLater(() -> {
				WindowNotifications.showInfo(window.getController().getCenterAnchorPane(), "Imported %d trees".formatted(phylogenies.size()));
				RunAfterAWhile.applyInFXThread(names, () -> window.getController().getRunMenuItem().fire());
			});
		} else {
			document.clear();
			var names = new ArrayList<String>();
			for (var network : phylogenies) {
				if (network.getName() == null || network.getName().isBlank()) {
					network.setName("N%03d".formatted(++count));
				}
				network.setName(StringUtils.getUniqueName(network.getName(), names));
				names.add(network.getName());
			}
			document.addNetworks(phylogenies);
			Platform.runLater(() -> WindowNotifications.showInfo(window.getController().getCenterAnchorPane(), "Imported %d networks".formatted(phylogenies.size())));
			Platform.runLater(() -> window.getPresenter().runUpdateNetworkDrawing());
		}
	}

	/**
	 * import from a buffered reader
	 *
	 * @param r reader
	 * @return set of new nodes
	 * @throws IOException
	 */
	public static List<PhyloTree> apply(BufferedReader r) throws IOException {
		var phylogenies = new ArrayList<PhyloTree>();
		var newickIO = new NewickIO();
		newickIO.setNewickNodeCommentConsumer(CommentData.createDataNodeConsumer());
		newickIO.setNewickEdgeCommentConsumer(CommentData.createDataEdgeConsumer());

		while (r.ready()) {
			var line = r.readLine();
			if (line == null)
				break;
			if (!line.isBlank() && line.trim().startsWith("(")) {
				var tree = new PhyloTree();
				newickIO.parseBracketNotation(tree, line, true, false);
				phylogenies.add(tree);

				for (var e : tree.edges()) {
					if (e.getTarget().getInDegree() > 1)
						System.err.println(e + ": " + e.getData());
				}
			}
		}
		return phylogenies;
	}
}
