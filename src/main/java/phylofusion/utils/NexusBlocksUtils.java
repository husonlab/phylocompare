/*
 * NexusBlocksUtils.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.utils;

import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.util.Collection;
import java.util.TreeSet;

public class NexusBlocksUtils {
	public static Result setupBlocks(Collection<PhyloTree> phyloTrees) {
		var taxaBlock = new TaxaBlock();
		var treesBlock = new TreesBlock();
		var names = new TreeSet<String>();
		for (var tree : phyloTrees) {
			names.addAll(tree.nodeStream().filter(v -> v.isLeaf() && tree.getLabel(v) != null && !tree.getLabel(v).isBlank()).map(tree::getLabel).toList());
		}
		taxaBlock.addTaxaByNames(names);
		var partial = false;

		for (var tree : phyloTrees) {
			for (var v : tree.nodes()) {
				if (v.isLeaf() && tree.getLabel(v) != null && !tree.getLabel(v).isBlank())
					tree.addTaxon(v, taxaBlock.indexOf(tree.getLabel(v)));
			}
			if (!partial && IteratorUtils.size(tree.getTaxa()) < taxaBlock.getNtax())
				partial = true;

		}
		treesBlock.setRooted(true);
		treesBlock.setPartial(true);
		treesBlock.setReticulated(false);
		treesBlock.getTrees().addAll(phyloTrees);
		return new Result(taxaBlock, treesBlock);
	}

	public record Result(TaxaBlock taxaBlock, TreesBlock treesBlock) {
	}

}
