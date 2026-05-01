/*
 * Workaround.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.collections.ObservableList;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import phylofusion.trace.TreeTrace;
import phylofusion.window.TreeRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static phylofusion.trace.TreeTrace.getTT;
import static phylofusion.trace.TreeTrace.setTT;

public class Workaround {
	public static Map<Integer, Integer> computeTreeRenumberMapping(ObservableList<TreeRecord> treeRecords, List<PhyloTree> runTrees) {
		var map = new HashMap<Integer, Integer>();
		for (var i = 0; i < runTrees.size(); i++) {
			var runTree = runTrees.get(i);
			for (var j = 0; j < treeRecords.size(); j++) {
				var treeRecord = treeRecords.get(j);
				if (treeRecord.getTree().equals(runTree)) {
					map.put(i + 1, j + 1);
					break;
				}
			}
		}
		return map;
	}

	public static void applyTreeRenumberMapping(Map<Integer, Integer> treeRenumberMapping, PhyloTree network) {
		var allInputTrees = treeRenumberMapping.keySet();
		var allTraceTrees = BitSetUtils.union(network.nodeStream().map(TreeTrace::getTT).toList());

		if (!allInputTrees.equals(allTraceTrees)) {
			for (var v : network.nodes()) {
				var tracedSet = getTT(v);
				if (tracedSet != null) {
					var adjustedSet = BitSetUtils.asBitSet(BitSetUtils.asStream(tracedSet).mapToInt(treeRenumberMapping::get).toArray());
					setTT(v, adjustedSet);
				}
			}
			for (var e : network.edges()) {
				var tracedSet = getTT(e);
				if (tracedSet != null) {
					var adjustedSet = BitSetUtils.asBitSet(BitSetUtils.asStream(tracedSet).mapToInt(treeRenumberMapping::get).toArray());
					setTT(e, adjustedSet);
				}
			}
		}
	}
}
