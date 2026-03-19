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

package phylofusion.view;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import jloda.fx.util.ColorSchemeManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import phylofusion.utils.HoverShadow;
import phylofusion.window.TreeRow;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import static phylofusion.trace.CompleteTreeTrace.getIsSet;

public class DrawTracedTrees {

	public static Group apply(PhyloTree network, List<TreeRow> treeRow, BitSet trees, double outlineWidth, Function<Node, Point2D> nodePointFunction, Function<Edge, Path> edgePathFunction, VBox legend) {
		if (!legend.getChildren().isEmpty())
			legend.getChildren().setAll(legend.getChildren().get(0));
		var group = new Group();

		var colorScheme = ColorSchemeManager.getInstance().getColorScheme("Retro29");

		var treeColorMap = new HashMap<Integer, Color>();
		var treeGroupMap = new HashMap<Integer, Group>();
		for (var treeId : BitSetUtils.members(trees)) {
			var color = colorScheme.get(treeId % colorScheme.size());
			treeColorMap.put(treeId, color);
			var treeGroup = new Group();
			treeGroupMap.put(treeId, treeGroup);
			group.getChildren().add(treeGroup);

			var label = new Text(treeRow.get(treeId - 1).getName());
			label.setFill(color);
			legend.getChildren().add(new HBox(new Text(" "), label));
			addHoverEffect(color, treeGroup, label);

		}

		var nTrees = trees.cardinality();
		if (nTrees > 0) {
			var treeOffsetMap = new HashMap<Integer, Double>();

			var d = outlineWidth / (nTrees + 1);
			var m = outlineWidth / 2;
			var total = d;
			for (var treeId : BitSetUtils.members(trees)) {
				treeOffsetMap.put(treeId, total - m);
				total += d;
			}

			System.err.println("width: " + outlineWidth);
			for (var entry : treeOffsetMap.entrySet()) {
				System.err.println("tree: " + entry.getKey() + " offset: " + entry.getValue() + " color: " + treeColorMap.get(entry.getKey()));
			}


			for (var e : network.edges()) {
				var use = BitSetUtils.copy(trees);
				var sourceSet = getIsSet(e.getSource());
				if (sourceSet != null) {
					use.and(sourceSet);
				}
				var targetSet = getIsSet(e.getTarget());
				if (targetSet != null) {
					use.and(targetSet);
				}
				var edgeSet = getIsSet(e);
				if (edgeSet != null) {
					System.err.println(e + ": " + edgeSet);
					use.and(edgeSet);
				}

				for (var treeId : BitSetUtils.members(use)) {
					var path = PathUtils.copy(edgePathFunction.apply(e));
					path.setEffect(null);
					path.setStrokeWidth(1);
					path.setStroke(treeColorMap.get(treeId));
					path.setTranslateX(treeOffsetMap.get(treeId));
					path.setTranslateY(treeOffsetMap.get(treeId));
					treeGroupMap.get(treeId).getChildren().add(path);
				}
			}
		}

		return group;
	}

	public static void addHoverEffect(Color color, javafx.scene.Node... nodes) {
		var hoverEffect = new HoverShadow(color, 2);
		for (var node : nodes) {
			node.setOnMouseEntered(e -> {
				for (var other : nodes) {
					other.setEffect(hoverEffect);
				}
			});
			node.setOnMouseExited(e -> {
				for (var other : nodes) {
					other.setEffect(null);
				}
			});
		}

	}
}
