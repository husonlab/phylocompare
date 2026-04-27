/*
 * AlgorithmsService.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.algorithm;

import javafx.application.Platform;
import javafx.scene.layout.Pane;
import jloda.fx.util.AService;
import jloda.fx.windownotifications.WindowNotifications;
import jloda.phylo.PhyloTree;
import phylofusion.trace.BruteForceTreeTracer;
import phylofusion.utils.NexusBlocksUtils;
import phylofusion.window.MainWindow;
import splitstree6.algorithms.trees.trees2trees.PhyloFusion;
import splitstree6.xtra.phyloFusionTreeTrace.PhyloFusionTreeTrace;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AlgorithmsService extends AService<Boolean> {

	public AlgorithmsService(Pane bottomPane) {
		super(bottomPane);
	}

	public void setupCalculation(MainWindow mainWindow, boolean runPhyloFusion, boolean runBruteForceTraceTrees) {
		setCallable(() -> {
			var document = mainWindow.getDocument();
			var progress = getProgressListener();

			var networks = new ArrayList<PhyloTree>();
			if (runPhyloFusion && document.hasTreesProperty().get()) {
				progress.setTasks("Running", "PhyloFusion");
				final List<PhyloTree> trees;
				if (document.getConfidenceThreshold() > 0.0)
					trees = FilterTrees.apply(document.getTaxaBlock(), document.getRunTrees(), mainWindow.getDocument().getConfidenceThreshold(), getProgressListener());
				else
					trees = document.getRunTrees();
				var blocks = NexusBlocksUtils.setupBlocks(document.getTaxaBlock(), trees);
				var resultBlock = new TreesBlock();
				var algorithm = new PhyloFusionTreeTrace();
				if (trees.size() == 1) {
					networks.add(trees.get(0));
				} else {
					algorithm.setOptionMutualRefinement(true);
					algorithm.compute(getProgressListener(), blocks.taxaBlock(), blocks.treesBlock(), resultBlock);
					for (var network : resultBlock.getTrees()) {
						if (network.getRoot().getOutDegree() > 1) {
							var v = network.getRoot();
							network.setRoot(network.newNode());
							var e = network.newEdge(network.getRoot(), v);
							if (network.hasEdgeWeights()) {
								network.setWeight(e, 0.00001);
							}

						}
					}
					networks.addAll(resultBlock.getTrees());
				}
			} else {
				networks.addAll(document.getNetworks());
			}
			if (progress.isUserCancelled())
				return false;
			if (runBruteForceTraceTrees && document.hasTreesProperty().get()) {
				getProgressListener().setTasks("Tree tracing", "");
				for (var network : networks) {
					BruteForceTreeTracer.apply(document.getTaxaBlock(), network, document.getTreeRecords(), document.getConfidenceThreshold(), progress);
				}
			}
			Platform.runLater(() -> {
				try {
					document.getNetworks().clear();
					document.addNetworks(networks);
				} catch (IOException e) {
					WindowNotifications.showError(mainWindow.getController().getCenterPane(), e.getMessage());
				}
			});
			return true;
		});
	}
}
