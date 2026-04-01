/*
 * CompleteTreeTrace.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.trace;

import jloda.graph.DAGTraversals;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;

import java.util.BitSet;

/**
 * for a given rooted network with tree tracing id assignments on the root, leaves
 * and reticulate edges, extends is to the complete network
 * Daniel Huson, 3.2026
 */
public class CompleteTreeTrace {

	/**
	 * extend index set to all edges
	 *
	 * @param network the network, with IS annotations on leaves and reticulate edges
	 */
	public static void apply(PhyloTree network) {
		// ensure network has required initial annotations:
		for (var v : network.nodes()) {
			if ((v.isLeaf() || v == network.getRoot()) && TreeTrace.getTT(v) == null)
				throw new RuntimeException("Leaves and root don't have valid index set data");
		}
		// ensure reticulate edges have required initial annotations:
		for (var e : network.edges()) {
			if (e.getTarget().getInDegree() > 1 && TreeTrace.getTT(e) == null)
				throw new RuntimeException("Reticulate edges don't have valid index set data");
		}

		DAGTraversals.postOrderTraversal(network.getRoot(), v -> {
			if (!v.isLeaf()) {
				var set = new BitSet();
				for (var e : v.outEdges()) {
					if (e.getTarget().getInDegree() < 2) {
						set.or(TreeTrace.getTT(e.getTarget()));
					} else {
						if (TreeTrace.getTT(e.getTarget()) != null) {
							set.or(BitSetUtils.intersection(TreeTrace.getTT(e.getTarget()), TreeTrace.getTT(e)));
						}
					}
				}
				var vSet = TreeTrace.getTT(v);
				if (vSet == null) {
					TreeTrace.setTT(v, set);
				} else {
					vSet.or(set);
				}
			}
		}, true);
	}
}
