/*
 * DrawTracedTrees.java Copyright (C) 2026 Daniel H. Huson
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

package phyloparallelograms.view;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import jloda.fx.util.ColorSchemeManager;
import jloda.graph.Edge;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import phyloparallelograms.trace.TreeTrace;
import phyloparallelograms.utils.HoverShadow;
import phyloparallelograms.window.TreeRecord;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import static phyloparallelograms.trace.TreeTrace.getTT;

public class DrawTracedTrees {

	public static Group apply(PhyloTree network, String colorSchemeName, List<TreeRecord> treeRecords, BitSet trees, double outlineWidth, Function<Edge, Path> edgePathFunction, Legend legend) {
		legend.clear();
		var group = new Group();

		var colorScheme = ColorSchemeManager.getInstance().getColorScheme(colorSchemeName);

		var treeColorMap = new HashMap<Integer, Color>();
		var treeGroupMap = new HashMap<Integer, Group>();
		for (var treeRecord : treeRecords) {
			var treeId = treeRecord.getId();
			if (trees.get(treeId)) {
				var color = (treeRecord.getColor() != null ? treeRecord.getColor() : colorScheme.get(treeId % colorScheme.size()));

				treeColorMap.put(treeId, color);
				var treeGroup = new Group();
				treeGroupMap.put(treeId, treeGroup);
				group.getChildren().add(treeGroup);

				var legendItem = legend.add(treeId, treeRecord, color);
				addHoverEffect(color, legendItem, treeGroup);
			}
		}

		var nTrees = trees.cardinality();
		if (nTrees > 0) {
			var treeOffsetMap = new HashMap<Integer, Double>();

			var d = outlineWidth / (nTrees + 1);
			var m = outlineWidth / 2;
			var total = d;

			for (var treeRecord : treeRecords) {
				var treeId = treeRecord.getId();
				if (trees.get(treeId)) {
					treeOffsetMap.put(treeId, total - m);
					total += d;
				}
			}

			if (false) {
				System.err.println("width: " + outlineWidth);
				for (var entry : treeOffsetMap.entrySet()) {
					System.err.println("tree: " + entry.getKey() + " offset: " + entry.getValue() + " color: " + treeColorMap.get(entry.getKey()));
				}
			}

			for (var e : network.edges()) {
				var use = BitSetUtils.copy(trees);
				var sourceSet = getTT(e.getSource());
				if (sourceSet != null) {
					use.and(sourceSet);
				}
				var targetSet = getTT(e.getTarget());
				if (targetSet != null) {
					use.and(targetSet);
				}
				var edgeSet = getTT(e);
				if (edgeSet != null) {
					use.and(edgeSet);
				}

				for (var treeId : BitSetUtils.members(use)) {
					var origPath = edgePathFunction.apply(e);
					if (origPath != null) {
						var path = PathUtils.copy(origPath);
						path.setEffect(null);
						path.setStrokeWidth(1);
						if (e.getTarget().getInDegree() > 1) {
							var countIncoming = (int) e.getTarget().inEdgesStream(false)
									.map(TreeTrace::getTT).filter(s -> s != null && s.get(treeId)).count();
							path.setStroke(adjusted(treeColorMap.get(treeId), countIncoming));
						} else
							path.setStroke(treeColorMap.get(treeId));

						path.setTranslateX(treeOffsetMap.get(treeId));
						path.setTranslateY(treeOffsetMap.get(treeId));
						treeGroupMap.get(treeId).getChildren().add(path);
					}
				}
			}
		}
		return group;
	}

	private static Color adjusted(Color base, int k) {
		if (k <= 1)
			return base;
		else return new Color(base.getRed(), base.getGreen(), base.getBlue(), 0.3);

		//var t = Math.min(0.8, 1.0 - 1.0 / Math.sqrt(k));
		//return base.interpolate(Color.WHITE, t);
	}

	public static void addHoverEffect(Color color, Legend.LegendItem legendItem, javafx.scene.Node treeGroup) {
		var hoverEffect = new HoverShadow(color, 2);

		legendItem.highlightedProperty().addListener((v, o, n) -> {
			treeGroup.setEffect(n ? hoverEffect : null);
		});

		treeGroup.setOnMouseEntered(e -> {
			legendItem.highlightedProperty().set(true);
		});

		treeGroup.setOnMouseExited(e -> {
			if (!legendItem.selectedProperty().get()) {
				legendItem.highlightedProperty().set(false);
			}
		});
	}
}
