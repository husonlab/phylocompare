
/*
 * PhyloFusionService.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.scene.layout.Pane;
import jloda.fx.util.AService;
import jloda.phylo.PhyloTree;
import phylofusion.utils.NexusBlocksUtils;
import splitstree6.algorithms.trees.trees2trees.PhyloFusion;
import splitstree6.data.TreesBlock;

import java.util.List;


public class PhyloFusionService extends AService<List<PhyloTree>> {
	public PhyloFusionService(Pane progressPane) {
		super(progressPane);
	}

	public void setupCalculation(List<PhyloTree> trees0, double minConfidence) {
		setCallable(() -> {
			final List<PhyloTree> trees;
			if (minConfidence > 0.0)
				trees = FilterTrees.apply(trees0, minConfidence, getProgressListener());
			else
				trees = trees0;
			var blocks = NexusBlocksUtils.setupBlocks(trees);
			var resultBlock = new TreesBlock();
			var algorithm = new PhyloFusion();
			algorithm.compute(getProgressListener(), blocks.taxaBlock(), blocks.treesBlock(), resultBlock);
			for (var network : resultBlock.getTrees()) {
				if (network.getRoot().getOutDegree() > 1) {
					var v = network.getRoot();
					network.setRoot(network.newNode());
					var e = network.newEdge(network.getRoot(), v);
					network.setWeight(e, 0.00001);
				}
			}
			return resultBlock.getTrees();
		});
	}
}
