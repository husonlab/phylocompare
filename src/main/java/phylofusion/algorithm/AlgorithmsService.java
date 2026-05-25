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
import jloda.util.BitSetUtils;
import phylofusion.trace.BruteForceTreeTracer;
import phylofusion.utils.NexusBlocksUtils;
import phylofusion.window.MainWindow;
import splitstree6.data.TreesBlock;
import splitstree6.xtra.phyloFusionTreeTrace.PhyloFusionTreeTrace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static phylofusion.trace.TreeTrace.getTT;
import static phylofusion.trace.TreeTrace.setTT;

public class AlgorithmsService extends AService<Boolean> {

	public AlgorithmsService(Pane bottomPane) {
		super(bottomPane);
	}

	public void setupCalculation(MainWindow mainWindow, boolean runPhyloFusion) {
		setCallable(() -> {
			var document = mainWindow.getDocument();
			var progress = getProgressListener();

			var networks = new ArrayList<PhyloTree>();
			if (runPhyloFusion && document.hasTreesProperty().get()) {
				progress.setTasks("Running", "PhyloFusion");
				final List<PhyloTree> trees;
				if (document.getApplicableConfidenceThreshold() > 0.0)
					trees = FilterTrees.apply(document.getTaxaBlock(), document.getRunTrees(), mainWindow.getDocument().getApplicableConfidenceThreshold(), getProgressListener());
				else
					trees = document.getRunTrees();

				var treeRenumberMapping = Workaround.computeTreeRenumberMapping(document.getTreeRecords(), trees);
				var blocks = NexusBlocksUtils.setupBlocks(document.getTaxaBlock(), trees);
				var resultBlock = new TreesBlock();
				if (trees.size() == 1) { // if there is only one tree, then it's the network
					var network = new PhyloTree(trees.get(0));
					network.nodeStream().forEach(v -> setTT(v, BitSetUtils.asBitSet(1)));
					Workaround.applyTreeRenumberMapping(treeRenumberMapping, network);
					networks.add(network);
				} else {
					if (false) {
						System.err.println("Input trees:");
						for (var tree : blocks.treesBlock().getTrees()) {
							System.err.println(tree.toBracketString(false) + ";");
						}
					}
					var algorithm = new PhyloFusionTreeTrace();
					algorithm.optionRefinementHeuristicProperty().set(false); // todo: this is broken, so turn off
					algorithm.compute(getProgressListener(), blocks.taxaBlock(), blocks.treesBlock(), resultBlock);
					if (false) {
						System.err.println("Output networks:");
						for (var network : resultBlock.getTrees()) {
							System.err.println(network.toBracketString(false) + ";");
						}
					}
					for (var network : resultBlock.getTrees()) {
						Workaround.applyTreeRenumberMapping(treeRenumberMapping, network);
						if (network.getRoot().getOutDegree() > 1) {
							var v = network.getRoot();
							network.setRoot(network.newNode());
							setTT(network.getRoot(), getTT(v));
							var e = network.newEdge(network.getRoot(), v);
							if (network.hasEdgeWeights()) {
								network.setWeight(e, 0.05 * network.edgeStream().mapToDouble(network::getWeight).max().orElse(1.0));
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
			if (BruteForceTreeTracer.requireTracing(networks, document.getTreeRecords())) {
				getProgressListener().setTasks("Tree tracing", "");
				if (false) {
					for (var network : networks) {
						for (var v : network.nodes()) {
							v.setData(null);
						}
						for (var e : network.edges()) {
							e.setData(null);
						}
					}
				}
				for (var network : networks) {
					BruteForceTreeTracer.apply(document.getTaxaBlock(), network, document.getTreeRecords(), document.getApplicableConfidenceThreshold(), progress);
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
