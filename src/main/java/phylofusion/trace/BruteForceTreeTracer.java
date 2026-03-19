/*
 * BruteForceTreeTracer.java Copyright (C) 2026 Daniel H. Huson
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
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressSilent;
import phylofusion.algorithm.FilterTrees;
import phylofusion.window.TreeRow;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static phylofusion.trace.CompleteTreeTrace.getIsSet;

/**
 * uses brute force trace of all shown trees in the network
 * Daniel Huson, 3.2026
 */
public class BruteForceTreeTracer {

	/**
	 * for each tree row for which 'show' is true and no trace is available,
	 * determine the reticulate edges that it uses
	 *
	 * @param network  the rooted network
	 * @param treeRows the tree rows
	 */
	public static void apply(PhyloTree network, List<TreeRow> treeRows, double minConfidence) {
		var requireComputation = new ArrayList<TreeRow>();

		var set = getIsSet(network.getRoot());
		for (var row : treeRows) {
			if (row.isShow() && (set == null || !set.get(row.getId())))
				requireComputation.add(row);
		}

		if (set == null) {
			// 1. setup comment data for root, leaves and reticulate edges
			for (var v : network.nodes()) {
				if ((v.isLeaf() || v == network.getRoot())) {
					CompleteTreeTrace.setIsSet(v);
				} else v.setData(null);
			}
			for (var e : network.edges()) {
				if (network.isReticulateEdge(e)) {
					CompleteTreeTrace.setIsSet(e);
				} else e.setData(null);
			}
		}

		if (!requireComputation.isEmpty()) {
			// generate all combinations of choices of reticulation edges:
			var reticulateNodes = network.nodeStream().filter(v -> v.getInDegree() > 1).toList();
			var choices = allChoicesOfReticulateEdges(reticulateNodes);

			try {
				ExecuteInParallel.apply(choices, choice -> {
					traceTrees(network, choice, requireComputation, minConfidence);
				}, ProgramExecutorService.getNumberOfCoresToUse());
			} catch (Exception e) {
				Basic.caught(e);
			}
			CompleteTreeTrace.apply(network);
		}
	}

	/**
	 * performs the tree-tracing analysis for a given choice of reticulate edges
	 *
	 * @param network               the network
	 * @param chosenReticulateEdges one edge per reticulation
	 * @param treeRows              the tree rows
	 */
	private static void traceTrees(PhyloTree network, Set<Edge> chosenReticulateEdges, List<TreeRow> treeRows, double minConfidence) {
		//System.err.println("Reticulate edges: "+StringUtils.toString(chosenReticulateEdges,", "));
		var networkClusters = extractAllClusters(network, chosenReticulateEdges);
		//System.err.println("Network clusters: "+StringUtils.toString(networkClusters,", "));
		var networkTaxa = BitSetUtils.union(networkClusters);
		var rootBitSet = getIsSet(network.getRoot());

		for (var row : treeRows) {
			if (row.isShow() && !rootBitSet.get(row.getId())) {
				PhyloTree tree = row.getTree();
				if (minConfidence > 0.0) {
					tree = FilterTrees.apply(List.of(tree), minConfidence, new ProgressSilent()).get(0);
				} else tree = row.getTree();
				var treeClusters = extractAllClusters(tree, Collections.emptySet());
				//System.err.println("Tree "+row.getId()+": "+row.getTree().toBracketString(false)+";");
				//System.err.println("Tree "+row.getId()+" clusters: "+StringUtils.toString(treeClusters,", "));

				var treeTaxa = BitSetUtils.union(treeClusters);
				if (treeTaxa.cardinality() == networkTaxa.cardinality()) {
					if (!allCompatible(networkClusters, treeClusters))
						continue;
				} else {
					var inducedNetworkClusters = networkClusters.stream()
							.map(c -> BitSetUtils.intersection(c, treeTaxa))
							.filter(c -> c.cardinality() > 0).collect(Collectors.toSet());
					if (!allCompatible(inducedNetworkClusters, treeClusters))
						continue;
				}
				// tree is contained in network using selected reticulate edges
				for (var v : network.nodes()) {
					if (v.isLeaf() || v == network.getRoot()) {
						getIsSet(v).set(row.getId());
					}
				}
				for (var e : chosenReticulateEdges) {
					getIsSet(e).set(row.getId());
				}
			}
		}
	}

	private static Set<BitSet> extractAllClusters(PhyloTree phylogeny, Set<Edge> chosenReticulateEdges) {
		try (NodeArray<BitSet> nodeClusterMap = phylogeny.newNodeArray()) {
			Function<Node, List<Node>> nodeListFunction = u -> u.outEdgesStream(false)
					.filter(f -> f.getTarget().getInDegree() < 2 || chosenReticulateEdges.contains(f)).map(Edge::getTarget)
					.toList();
			Consumer<Node> consumer = v -> {
				if (v.isLeaf()) {
					nodeClusterMap.put(v, BitSetUtils.asBitSet(phylogeny.getTaxon(v)));
				} else {
					var cluster = new BitSet();
					for (var u : nodeListFunction.apply(v)) {
						cluster.or(nodeClusterMap.get(u));
					}
					nodeClusterMap.put(v, cluster);
				}
			};
			DAGTraversals.postOrderTraversal(phylogeny.getRoot(), nodeListFunction, consumer);
			return new HashSet<>(nodeClusterMap.values());
		}
	}

	public static List<Set<Edge>> allChoicesOfReticulateEdges(List<Node> nodes) {
		var choices = new ArrayList<Set<Edge>>(nodes.size());
		for (var n : nodes) {
			var set = IteratorUtils.asSet(n.inEdges());
			if (set.isEmpty()) {
				// If any node has no choices, there are no complete selections
				return List.of();
			}
			choices.add(set);
		}

		var result = new ArrayList<Set<Edge>>();
		allChoicesOfReticulateEdgesRec(choices, 0, new ArrayList<>(nodes.size()), result);
		return result;
	}

	private static <Edge> void allChoicesOfReticulateEdgesRec(List<Set<Edge>> choices, int i, List<Edge> current, List<Set<Edge>> out) {
		if (i == choices.size()) {
			out.add(new HashSet<>(current));
			return;
		}
		for (var e : choices.get(i)) {
			current.add(e);
			allChoicesOfReticulateEdgesRec(choices, i + 1, current, out);
			current.remove(current.size() - 1);
		}
	}

	private static boolean allCompatible(Collection<BitSet> clusters1, Collection<BitSet> clusters2) {
		for (var c1 : clusters1) {
			for (var c2 : clusters2) {
				var intersectionSize = BitSetUtils.intersection(c1, c2).cardinality();
				if (intersectionSize != 0 && intersectionSize != c1.cardinality() && intersectionSize != c2.cardinality())
					return false;
			}
		}
		return true;
	}
}
