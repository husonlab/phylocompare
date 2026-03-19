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
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.CommentData;
import jloda.phylo.PhyloTree;

import java.util.BitSet;

/**
 * for a given rooted network with tree tracing id assignments on the root, leaves
 * and reticulate edges, extends is to the complete network
 * Daniel Huson, 3.2026
 */
public class CompleteTreeTrace {
	public final static String KEY = "IS";

	/**
	 * extend index set to all edges
	 *
	 * @param network the network, with IS annotations on leaves and reticulate edges
	 */
	public static void apply(PhyloTree network) {
		// ensure network has required initial annotations:
		for (var v : network.nodes()) {
			if ((v.isLeaf() || v == network.getRoot()) && getIsSet(v) == null)
				throw new RuntimeException("Leaves and root don't have valid index set data");
		}
		// ensure reticulate edges have required initial annotations:
		for (var e : network.edges()) {
			if (e.getTarget().getInDegree() > 1 && getIsSet(e) == null)
				throw new RuntimeException("Reticulate edges don't have valid index set data");
		}

		DAGTraversals.postOrderTraversal(network.getRoot(), v -> {
			if (!v.isLeaf()) {
				var set = new BitSet();
				for (var e : v.outEdges()) {
					if (e.getTarget().getInDegree() < 2) {
						set.or(getIsSet(e.getTarget()));
					} else {
						set.or(getIsSet(e));
					}
				}
				var vSet = getIsSet(v);
				if (vSet == null) {
					setIsSet(v, set);
				} else {
					vSet.or(set);
				}
			}
		}, true);
	}

	public static BitSet getIsSet(Object nodeOrEdge) {
		if (nodeOrEdge instanceof Node v && v.getData() instanceof CommentData data) {
			return data.getIntSetValue(KEY).orElse(null);
		} else if (nodeOrEdge instanceof Edge e && e.getData() instanceof CommentData data) {
			return data.getIntSetValue(KEY).orElse(null);
		} else return null;
	}

	public static void setIsSet(Object nodeOrEdge) {
		setIsSet(nodeOrEdge, new BitSet());
	}

	public static void setIsSet(Object nodeOrEdge, BitSet set) {
		var commentData = new CommentData();
		commentData.put(CompleteTreeTrace.KEY, set);
		if (nodeOrEdge instanceof Node v) {
			v.setData(commentData);
		} else if (nodeOrEdge instanceof Edge e) {
			e.setData(commentData);
		}
	}
}
