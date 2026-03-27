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
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.phylogeny.layout.Averaging;
import jloda.util.BitSetUtils;
import phylofusion.window.TreeRecord;
import splitstree6.data.TaxaBlock;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.TreeDiagramType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class NetworkView extends Group {
	private final NetworkViewService service;
	private final VBox legend;
	private final Group networkGroup = new Group();
	private final Group outlinesGroup = new Group();
	private final Group tracedTreesGroup = new Group();
	private final ObservableMap<Edge, Path> edgeOutlineMap = FXCollections.observableHashMap();
	private final Map<Node, LabeledNodeShape> nodeLabeledNodeShapeMap = new HashMap<>();
	private final Map<Edge, LabeledEdgeShape> edgeLabeledEdgeShapeHashMap = new HashMap<>();

	private final ObjectProperty<TreeDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", TreeDiagramType.RectangularCladogram);
	private final ObjectProperty<Averaging> optionAveraging = new SimpleObjectProperty<>(this, "optionAveraging", Averaging.ChildAverage);
	private final DoubleProperty optionOutlineWidth = new SimpleDoubleProperty(this, "optionOutlineWidth", 30.0);
	private final BooleanProperty optionShowOutline = new SimpleBooleanProperty(this, "optionShowOutline", true);

	private final BooleanProperty optionRectangularEdges = new SimpleBooleanProperty(this, "optionRectangularEdges", false);
	private final BooleanProperty optionReticulateEdgesAreSpecial = new SimpleBooleanProperty(this, "optionReticulateEdgesAreSpecial", true);

	{
		ProgramProperties.track(optionOutlineWidth, 30.0);
	}


	private final DoubleProperty targetWidth = new SimpleDoubleProperty(this, "targetWidth", 800.0);
	private final DoubleProperty targetHeight = new SimpleDoubleProperty(this, "targetHeight", 800.0);

	static int countDrawNetworks = 0;
	static int countDrawTrees = 0;

	public NetworkView(Pane bottomPane, VBox legend) {
		this.service = new NetworkViewService(bottomPane);
		this.legend = legend;

		optionShowOutline.addListener((v, o, n) -> {
			outlinesGroup.setEffect(n ? new DropShadow(BlurType.THREE_PASS_BOX, MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK, 1, 0.5, 0.0, 0.0) : null);
		});
		outlinesGroup.setEffect(optionShowOutline.get() ? new DropShadow(BlurType.THREE_PASS_BOX, MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK, 1, 0.5, 0.0, 0.0) : null);

		MainWindowManager.useDarkThemeProperty().addListener((v, o, n) -> {
			if (optionShowOutline.get())
				outlinesGroup.setEffect(new DropShadow(BlurType.THREE_PASS_BOX, n ? Color.WHITE : Color.BLACK, 1, 0.5, 0.0, 0.0));
			for (var shape : BasicFX.getAllRecursively(outlinesGroup, Path.class)) {
				shape.setStroke(n ? Color.WHITE : Color.BLACK);
			}
		});

		getChildren().addAll(networkGroup, tracedTreesGroup);
	}

	public void clear() {
		edgeOutlineMap.clear();
		networkGroup.getChildren().clear();
		outlinesGroup.getChildren().clear();
		tracedTreesGroup.getChildren().clear();
		legend.getChildren().setAll(legend.getChildren().get(0));
	}

	public void clearTracedTreesDrawing() {
		tracedTreesGroup.getChildren().clear();
		legend.getChildren().setAll(legend.getChildren().get(0));
	}

	public void update(List<TreeRecord> treeRecords, PhyloTree network, double scaleFactor, boolean updateNetworkDrawing, boolean updateTreesDrawing) {
		if (updateNetworkDrawing) {
			clear();
			var taxaBlock = new TaxaBlock();
			taxaBlock.addTaxaByNames(network.nodeStream().filter(v -> v.isLeaf() && network.getLabel(v) != null && !network.getLabel(v).isBlank()).map(network::getLabel).toList());

			var width = scaleFactor * Math.max(400, getTargetWidth() - 200);
			var height = scaleFactor * Math.max(400, getTargetHeight() - 50);
			service.setup(taxaBlock, network, getOptionDiagram(), getOptionAveraging(), width, height, optionReticulateEdgesAreSpecial.get());
			service.setOnSucceeded(a -> {
				System.err.println("Draw Network " + (++countDrawNetworks));
				var result = service.getValue();
				networkGroup.getChildren().setAll(result.getAllAsGroup());
				networkGroup.getChildren().add(outlinesGroup);
				nodeLabeledNodeShapeMap.clear();
				nodeLabeledNodeShapeMap.putAll(service.getNodeLabeledNodeShapeMap());
				// put space in front and end of labels:
				nodeLabeledNodeShapeMap.values().stream().map(LabeledNodeShape::getLabel).filter(Objects::nonNull).forEach(label -> label.setText("   %s   ".formatted(label.getText())));

				edgeLabeledEdgeShapeHashMap.clear();
				edgeLabeledEdgeShapeHashMap.putAll(service.getEdgeLabeledEdgeShapeHashMap());
				drawOutline(getOptionOutlineWidth());

				if (updateTreesDrawing) {
					clearTracedTreesDrawing();
					System.err.println("Draw Trees " + (++countDrawTrees));
					drawTracedTrees(network, treeRecords);
				}
			});
		} else if (updateTreesDrawing) {
			clearTracedTreesDrawing();
			System.err.println("Draw Trees " + (++countDrawTrees));
			drawTracedTrees(network, treeRecords);
		}
	}

	public void drawOutline(double outlineWidth) {
		outlinesGroup.getChildren().clear();
		edgeOutlineMap.clear();

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
		}
	}

	private void drawTracedTrees(PhyloTree network, List<TreeRecord> treeRecords) {
		var trees = BitSetUtils.asBitSet(treeRecords.stream().filter(TreeRecord::isShow).mapToInt(TreeRecord::getId).toArray());
		Function<Node, Point2D> nodePointFunction = v -> {
			var shape = nodeLabeledNodeShapeMap.get(v);
			return new Point2D(shape.getTranslateX(), shape.getTranslateY());
		};
		Function<Edge, Path> edgePathFunction = e -> (Path) edgeLabeledEdgeShapeHashMap.get(e).getShape();
		tracedTreesGroup.getChildren().setAll(DrawTracedTrees.apply(network, treeRecords, trees, getOptionOutlineWidth(), nodePointFunction, edgePathFunction, legend));
	}

	public TreeDiagramType getOptionDiagram() {
		return optionDiagram.get();
	}

	public void setOptionDiagram(TreeDiagramType optionDiagram) {
		this.optionDiagram.set(optionDiagram);
	}

	public ObjectProperty<TreeDiagramType> optionDiagramProperty() {
		return optionDiagram;
	}

	public Averaging getOptionAveraging() {
		return optionAveraging.get();
	}

	public ObjectProperty<Averaging> optionAveragingProperty() {
		return optionAveraging;
	}

	public double getOptionOutlineWidth() {
		return optionOutlineWidth.get();
	}

	public DoubleProperty optionOutlineWidthProperty() {
		return optionOutlineWidth;
	}

	public boolean isOptionShowOutline() {
		return optionShowOutline.get();
	}

	public BooleanProperty optionShowOutlineProperty() {
		return optionShowOutline;
	}

	public double getTargetWidth() {
		return targetWidth.get();
	}

	public DoubleProperty targetWidthProperty() {
		return targetWidth;
	}

	public double getTargetHeight() {
		return targetHeight.get();
	}

	public DoubleProperty targetHeightProperty() {
		return targetHeight;
	}

	public ReadOnlyBooleanProperty runningProperty() {
		return service.runningProperty();
	}

	public BooleanProperty optionRectangularEdgesProperty() {
		return optionRectangularEdges;
	}

	public BooleanProperty optionReticulateEdgesAreSpecialProperty() {
		return optionReticulateEdgesAreSpecial;
	}
}

