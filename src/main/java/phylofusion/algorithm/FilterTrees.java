/*
 * FilterTrees.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.trees.trees2trees.TreesEdgesFilter;
import splitstree6.data.TreesBlock;

import java.util.List;

import static phylofusion.utils.NexusBlocksUtils.setupBlocks;

public class FilterTrees {
	public static List<PhyloTree> apply(List<PhyloTree> trees, double minConfidence, ProgressListener progress) {
		if (minConfidence > 0.0) {
			var blocks = setupBlocks(trees);
			var workingTrees = new TreesBlock();
			var algorithm = new TreesEdgesFilter();
			algorithm.setOptionMinConfidence(minConfidence);
			algorithm.compute(progress, blocks.taxaBlock(), blocks.treesBlock(), workingTrees);
			return workingTrees.getTrees();
		} else return trees;
	}
}
