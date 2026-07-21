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

package phyloparallelograms.view;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.collections.SetChangeListener;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import jloda.fx.options.Option;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.SelectionEffectBlue;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.phylogeny.layout.Averaging;
import jloda.phylogeny.layout.LayoutRootedPhylogeny;
import jloda.util.BitSetUtils;
import phyloparallelograms.window.SetupContextMenuTaxonLabels;
import phyloparallelograms.window.TreeRecord;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.TreeDiagramType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NetworkView extends Group {
	private final NetworkViewService service;
	private final Legend legend;
	private final Group networkGroup = new Group();
	private final Group outlinesGroup = new Group();
	private final Group tracedTreesGroup = new Group();
	private final ObservableMap<Edge, Path> edgeOutlineMap = FXCollections.observableHashMap();
	private final Map<Node, LabeledNodeShape> nodeLabeledNodeShapeMap = new HashMap<>();
	private final Map<Taxon, LabeledNodeShape> taxonLabeledNodeShapeMap = new HashMap<>();
	private final Map<Edge, LabeledEdgeShape> edgeLabeledEdgeShapeHashMap = new HashMap<>();

	@Option(description = "Type of tree diagram to draw")
	private final ObjectProperty<TreeDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", TreeDiagramType.RectangularCladogram);
	@Option(description = "Flip the drawing vertically")
	private final BooleanProperty optionFlipVertically = new SimpleBooleanProperty(this, "optionFlipVertically", false);
	@Option(description = "How to average the positions of nodes")
	private final ObjectProperty<Averaging> optionAveraging = new SimpleObjectProperty<>(this, "optionAveraging", Averaging.ChildAverage);
	@Option(description = "How to scale the drawing")
	private final ObjectProperty<LayoutRootedPhylogeny.Scaling> optionScaling = new SimpleObjectProperty<>(this, "optionScaling", LayoutRootedPhylogeny.Scaling.LateBranching);
	@Option(description = "Width of the outline drawn around traced trees", min = 0, max = 100)
	private final DoubleProperty optionOutlineWidth = new SimpleDoubleProperty(this, "optionOutlineWidth", 30.0);
	@Option(description = "Show the outline around traced trees")
	private final BooleanProperty optionShowOutline = new SimpleBooleanProperty(this, "optionShowOutline", false);
	@Option(description = "Percentage of the reticulate edge that is attributed to the acceptor", min = 50, max = 100)
	private final DoubleProperty optionAcceptorPercentage = new SimpleDoubleProperty(this, "optionAcceptorPercentage", 75);
	@Option(description = "Show reticulate edges as transfer edges", aliases = "use_transfer")
	private final BooleanProperty optionShowTransfer = new SimpleBooleanProperty(this, "optionShowTransfer", false);

	@Option(description = "Draw edges as rectangular lines")
	private final BooleanProperty optionRectangularEdges = new SimpleBooleanProperty(this, "optionRectangularEdges", false);
	@Option(description = "Draw reticulate edges in a special style")
	private final BooleanProperty optionReticulateEdgesAreSpecial = new SimpleBooleanProperty(this, "optionReticulateEdgesAreSpecial", true);

	private final SelectionModel<Taxon> taxonSelectionModel;

	{
		ProgramProperties.track(optionDiagram, TreeDiagramType::valueOf, TreeDiagramType.RectangularCladogram);
		ProgramProperties.track(optionAveraging, Averaging::valueOf, Averaging.ChildAverage);
		ProgramProperties.track(optionScaling, LayoutRootedPhylogeny.Scaling::valueOf, LayoutRootedPhylogeny.Scaling.LateBranching);
		ProgramProperties.track(optionOutlineWidth, 30.0);
		ProgramProperties.track(optionShowOutline, false);
		ProgramProperties.track(optionAcceptorPercentage, 75.0);
		ProgramProperties.track(optionShowTransfer, false);
		ProgramProperties.track(optionReticulateEdgesAreSpecial, true);
	}

	private final Pane centerPane;

	private final DoubleProperty targetWidth = new SimpleDoubleProperty(this, "targetWidth", 800.0);
	private final DoubleProperty targetHeight = new SimpleDoubleProperty(this, "targetHeight", 800.0);

	public NetworkView(SelectionModel<Taxon> taxonSelectionModel, Pane centerPane, Pane bottomPane, Legend legend) {
		this.taxonSelectionModel = taxonSelectionModel;
		this.centerPane = centerPane;
		this.service = new NetworkViewService(bottomPane);
		this.legend = legend;

		var lightEffect = new DropShadow(BlurType.THREE_PASS_BOX, Color.LIGHTGRAY, 0.3, 0.9, 0.0, 0.0);

		var darkEffect = new DropShadow(BlurType.THREE_PASS_BOX, Color.DARKGRAY, 0.3, 0.9, 0.0, 0.0);

		optionShowOutline.addListener((v, o, n) -> {
			outlinesGroup.setEffect(n ? (MainWindowManager.isUseDarkTheme() ? darkEffect : lightEffect) : null);
		});
		outlinesGroup.setEffect(isOptionShowOutline() ? (MainWindowManager.isUseDarkTheme() ? darkEffect : lightEffect) : null);

		MainWindowManager.useDarkThemeProperty().addListener((v, o, n) -> {
			outlinesGroup.setEffect(isOptionShowOutline() ? (n ? darkEffect : lightEffect) : null);
		});

		taxonSelectionModel.getSelectedItems().addListener((SetChangeListener<? super Taxon>) e -> {
			if (e.wasAdded()) {
				var label = taxonLabeledNodeShapeMap.get(e.getElementAdded()).getLabel();
				if (label != null) {
					label.setEffect(SelectionEffectBlue.getInstance());
				}
			}
			if (e.wasRemoved()) {
				var label = taxonLabeledNodeShapeMap.get(e.getElementRemoved()).getLabel();
				if (label != null) {
					label.setEffect(null);
				}
			}
		});

		getChildren().addAll(networkGroup, tracedTreesGroup);
	}

	public void clear() {
		edgeOutlineMap.clear();
		networkGroup.getChildren().clear();
		outlinesGroup.getChildren().clear();
		tracedTreesGroup.getChildren().clear();
		legend.clear();
		taxonSelectionModel.getSelectedItems().clear();
	}

	public void clearTracedTreesDrawing() {
		tracedTreesGroup.getChildren().clear();
		legend.clear();
	}

	public void update(TaxaBlock taxaBlock, List<TreeRecord> treeRecords, PhyloTree network, double scaleFactorX, double scaleFactorY, boolean updateNetworkDrawing, boolean updateTreesDrawing, String colorSchemeName) {
		if (updateNetworkDrawing) {
			clear();
			var width = scaleFactorX * Math.max(400, getTargetWidth() - 200);
			var height = scaleFactorY * Math.max(400, getTargetHeight() - 50);
			service.setup(taxaBlock, network, getOptionDiagram(), getOptionAveraging(), getOptionScaling(), width, height, optionReticulateEdgesAreSpecial.get(), getApplicableAcceptorPercentage());
			service.setOnSucceeded(a -> {
				var result = service.getValue();
				var labelsGroup = result.taxonLabels();
				var all = result.getAllAsGroup();
				all.getChildren().remove(labelsGroup);
				networkGroup.getChildren().setAll(result.getAllAsGroup());
				networkGroup.getChildren().add(outlinesGroup);
				networkGroup.getChildren().add(labelsGroup); // want labels on top of outline
				nodeLabeledNodeShapeMap.clear();
				nodeLabeledNodeShapeMap.putAll(service.getNodeLabeledNodeShapeMap());

				for (var v : nodeLabeledNodeShapeMap.keySet()) {
					if (network.hasTaxa(v)) {
						var taxon = taxaBlock.get(network.getTaxon(v));
						var shape = nodeLabeledNodeShapeMap.get(v);
						if (shape != null) {
							taxonLabeledNodeShapeMap.put(taxon, shape);
							var label = shape.getLabel();
							if (label != null) {
								SetupContextMenuTaxonLabels.apply(label, taxon, labelsGroup);

								ChangeListener<String> changeListener = (d, o, n) -> label.setText(n);

								label.setUserData(changeListener);
								if (taxon != null) {
									label.setText(taxon.getDisplayLabelOrName());
									taxon.displayLabelProperty().addListener(new WeakChangeListener<>(changeListener));
								}
								label.setOnMousePressed(Event::consume);
								label.setOnMouseClicked(e -> {
									if (!e.isShiftDown())
										taxonSelectionModel.clearSelection();
									if (taxon != null)
										taxonSelectionModel.toggleSelection(taxon);
									e.consume();
								});
							}
						}
					}
				}

				edgeLabeledEdgeShapeHashMap.clear();
				edgeLabeledEdgeShapeHashMap.putAll(service.getEdgeLabeledEdgeShapeHashMap());

				if (getOptionFlipVertically() && !getOptionDiagram().isRadialOrCircular()) {
					applyVerticalFlip();
				}

				drawOutline(getOptionOutlineWidth());

				if (updateTreesDrawing) {
					clearTracedTreesDrawing();
					drawTracedTrees(network, colorSchemeName, treeRecords);
				}
			});
		} else if (updateTreesDrawing) {
			clearTracedTreesDrawing();
			drawTracedTrees(network, colorSchemeName, treeRecords);
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
					if (e.getSource().getInDegree() == 0 || e.getTarget().getOutDegree() == 0)
						outline.setStrokeLineCap(StrokeLineCap.SQUARE);
					else
						outline.setStrokeLineCap(StrokeLineCap.ROUND);

					outline.setStrokeWidth(outlineWidth);

					//outline.setStroke(MainWindowManager.isUseDarkTheme() ? Color.BLACK : Color.WHITE);
					outline.getStyleClass().add("background-stroke");
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

	private void drawTracedTrees(PhyloTree network, String colorSchemeName, List<TreeRecord> treeRecords) {
		var trees = BitSetUtils.asBitSet(treeRecords.stream().filter(TreeRecord::isShow).mapToInt(TreeRecord::getId).toArray());
		Function<Edge, Path> edgePathFunction = e -> (Path) (edgeLabeledEdgeShapeHashMap.containsKey(e) ? edgeLabeledEdgeShapeHashMap.get(e).getShape() : null);
		tracedTreesGroup.getChildren().setAll(DrawTracedTrees.apply(network, colorSchemeName, treeRecords, trees, getOptionOutlineWidth(), edgePathFunction, legend));
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

	public LayoutRootedPhylogeny.Scaling getOptionScaling() {
		return optionScaling.get();
	}

	public ObjectProperty<LayoutRootedPhylogeny.Scaling> optionScalingProperty() {
		return optionScaling;
	}

	public double getOptionOutlineWidth() {
		return optionOutlineWidth.get();
	}

	public DoubleProperty optionOutlineWidthProperty() {
		return optionOutlineWidth;
	}

	public DoubleProperty optionAcceptorPercentageProperty() {
		return optionAcceptorPercentage;
	}

	public double getApplicableAcceptorPercentage() {
		return optionShowTransfer.get() ? optionAcceptorPercentage.get() : 100.0;
	}

	public BooleanProperty optionShowTransferProperty() {
		return optionShowTransfer;
	}

	public boolean isOptionShowOutline() {
		return optionShowOutline.get();
	}

	public BooleanProperty optionShowOutlineProperty() {
		return optionShowOutline;
	}

	public boolean getOptionFlipVertically() {
		return optionFlipVertically.get();
	}

	public BooleanProperty optionFlipVerticallyProperty() {
		return optionFlipVertically;
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

	public Legend getLegend() {
		return legend;
	}

	public Pane getCenterPane() {
		return centerPane;
	}

	public Map<Node, LabeledNodeShape> getNodeLabeledNodeShapeMap() {
		return nodeLabeledNodeShapeMap;
	}

	public Map<Edge, LabeledEdgeShape> getEdgeLabeledEdgeShapeHashMap() {
		return edgeLabeledEdgeShapeHashMap;
	}

	public void applyVerticalFlip() {
		var shapes = getNodeLabeledNodeShapeMap().values();
		var paths = getEdgeLabeledEdgeShapeHashMap().values().stream().map(es -> (Path) es.getShape()).toList();
		TransformNodesEdges.apply(shapes, paths, (x, y) -> new Point2D(x, -y));
	}
}

