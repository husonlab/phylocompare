/*
 * NetworkView.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import jloda.fx.util.BasicFX;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.phylogeny.layout.Averaging;
import splitstree6.data.TaxaBlock;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.TreeDiagramType;

import java.util.HashMap;
import java.util.Map;


public class NetworkView extends Pane {
	private final NetworkViewService service;
	private final Group outlinesGroup = new Group();
	private final ObservableMap<Edge, Path> edgeOutlineMap = FXCollections.observableHashMap();
	private final Map<Node, LabeledNodeShape> nodeLabeledNodeShapeMap = new HashMap<>();
	private final Map<Edge, LabeledEdgeShape> edgeLabeledEdgeShapeHashMap = new HashMap<>();

	public NetworkView(Pane bottomPane) {
		this.service = new NetworkViewService(bottomPane);

		outlinesGroup.setEffect(new DropShadow(BlurType.THREE_PASS_BOX, MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK, 0.5, 0.5, 0.0, 0.0));
		MainWindowManager.useDarkThemeProperty().addListener((v, o, n) -> {
			outlinesGroup.setEffect(new DropShadow(BlurType.THREE_PASS_BOX, n ? Color.WHITE : Color.BLACK, 0.5, 0.5, 0.0, 0.0));
			for (var shape : BasicFX.getAllRecursively(outlinesGroup, Path.class)) {
				shape.setStroke(n ? Color.WHITE : Color.BLACK);
			}
		});
	}

	public void update(PhyloTree network, TreeDiagramType diagram, Averaging averaging, double outlineWidth) {
		clear();

		var taxaBlock = new TaxaBlock();
		taxaBlock.addTaxaByNames(network.nodeStream().filter(v -> v.isLeaf() && network.getLabel(v) != null && !network.getLabel(v).isBlank()).map(network::getLabel).toList());

		var width = Math.max(400, getWidth() - 50);
		var height = Math.max(400, getHeight() - 50);
		service.setup(taxaBlock, network, diagram, averaging, width, height);
		service.setOnSucceeded(a -> {
			var result = service.getValue();
			var all = result.getAllAsGroup();
			all.getChildren().add(outlinesGroup);
			getChildren().setAll(all);
			nodeLabeledNodeShapeMap.clear();
			nodeLabeledNodeShapeMap.putAll(service.getNodeLabeledNodeShapeMap());
			edgeLabeledEdgeShapeHashMap.clear();
			edgeLabeledEdgeShapeHashMap.putAll(service.getEdgeLabeledEdgeShapeHashMap());
			drawOutline(outlineWidth);
		});
	}

	public void drawOutline(double outlineWidth) {
		outlinesGroup.getChildren().clear();
		edgeOutlineMap.clear();
		getChildren().remove(outlinesGroup);

		if (outlineWidth > 0) {
			for (var entry : edgeLabeledEdgeShapeHashMap.entrySet()) {
				var e = entry.getKey();
				var edgeShape = entry.getValue();
				if (!edgeOutlineMap.containsKey(e) && edgeShape.getShape() instanceof Path path) {
					var outline = PathUtils.copy(path);
					outline.getStyleClass().remove("graph-edge");
					if (e.getSource().getInDegree() == 0 || e.getTarget().getOutDegree() == 0)
						outline.setStrokeLineCap(StrokeLineCap.SQUARE);
					else
						outline.setStrokeLineCap(StrokeLineCap.ROUND);

					outline.setStrokeWidth(outlineWidth);

					outline.setStroke(MainWindowManager.isUseDarkTheme() ? Color.BLACK : Color.WHITE);
					outline.setFill(Color.TRANSPARENT);
					edgeOutlineMap.put(e, outline);
					InvalidationListener listener = b -> outline.getElements().setAll(PathUtils.copy(path.getElements()));
					outline.setUserData(listener); // keep a reference
					path.getElements().addListener(new WeakInvalidationListener(listener));
					var sourceShape = nodeLabeledNodeShapeMap.get(e.getSource()).getShape();
					sourceShape.translateXProperty().addListener(new WeakInvalidationListener(listener));
					var targetShape = nodeLabeledNodeShapeMap.get(e.getTarget()).getShape();
					targetShape.translateXProperty().addListener(new WeakInvalidationListener(listener));
					outlinesGroup.getChildren().add(outline);
				}
			}
			getChildren().add(outlinesGroup);
		}
	}

	public void clear() {
		getChildren().clear();
		outlinesGroup.getChildren().clear();
		edgeOutlineMap.clear();
	}
}
