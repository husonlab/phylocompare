

/*
 * Document.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.model;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.fx.util.BasicFX;
import jloda.fx.util.RunAfterAWhile;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CollectionUtils;
import jloda.util.StringUtils;
import phylofusion.trace.TreeTrace;
import phylofusion.window.TreeRecord;

import java.io.IOException;
import java.util.*;

public class Document {
	private final ObservableList<TreeRecord> treeRecords = FXCollections.observableArrayList();
	private final ObservableList<PhyloTree> networks = FXCollections.observableArrayList();

	private final BooleanProperty hasTreeRecords = new SimpleBooleanProperty(this, "hasTreesIds", false);
	private final BooleanProperty hasTrees = new SimpleBooleanProperty(this, "hasTrees", false);
	private final BooleanProperty hasNetworks = new SimpleBooleanProperty(this, "hasNetworks", false);
	private final BooleanProperty empty = new SimpleBooleanProperty(this, "emptyProperty", false);

	private final DoubleProperty confidenceThreshold = new SimpleDoubleProperty(this, "confidenceThreshold");

	public Document() {
		hasTreeRecords.bind(Bindings.isNotEmpty(treeRecords));
		hasNetworks.bind(Bindings.isNotEmpty(networks));
		empty.bind(hasTreeRecords.not().and(hasNetworks.not()));

		treeRecords.addListener((InvalidationListener) e ->
				RunAfterAWhile.applyInFXThread(treeRecords,
						() -> hasTrees.set(treeRecords.stream().allMatch(r -> r.getTree() != null))));

		BasicFX.reportChanges("hasTrees", hasTrees);
	}

	public void clear() {
		treeRecords.clear();
		networks.clear();
	}

	public ObservableList<TreeRecord> getTreeRecords() {
		return treeRecords;
	}

	public void addTrees(List<PhyloTree> trees) {
		var nextId = treeRecords.stream().mapToInt(TreeRecord::getId).max().orElse(0) + 1;
		for (var tree : trees) {
			if (tree.nodeStream().noneMatch(v -> v.getInDegree() > 1)) {
				if (tree.getName() == null || tree.getName().isBlank())
					tree.setName("T%03d".formatted(nextId));
				treeRecords.add(new TreeRecord(tree.getName(), nextId, true, true, tree));
				nextId++;
			}
		}
		addTaxa(treeRecords.stream().map(TreeRecord::getTree).toList());
	}

	public ObservableList<PhyloTree> getNetworks() {
		return networks;
	}

	public void addNetworks(Collection<PhyloTree> networks) throws IOException {
		for (var network : networks) {
			for (var v : network.nodes()) {
				if (TreeTrace.getTT(v) == null) {
					throw new IOException("Network has node without tree trace annotation: " + v);
				}
			}
			for (var e : network.edges()) {
				if (e.getTarget().getInDegree() > 1 && TreeTrace.getTT(e) == null) {
					throw new IOException("Network has reticulate edge without tree trace annotation: " + e);
				}
			}
		}

		var treeIds = new BitSet();
		for (var network : networks) {
			for (var v : network.nodes()) {
				treeIds.or(TreeTrace.getTT(v));
			}
			network.nodeStream().map(TreeTrace::getTT).filter(Objects::nonNull).forEach(treeIds::or);
		}
		if (treeIds.cardinality() == 0)
			throw new IOException("Network does not have tree trace annotations");
		else {
			System.err.println("Tree trace ids: " + StringUtils.toString(treeIds, " "));
		}

		for (var id : BitSetUtils.members(treeIds)) {
			var name = "tree-" + id;
			treeRecords.add(new TreeRecord(name, id, true, true, null));
		}
		addTaxa(networks);
		this.networks.addAll(networks);
	}

	public void addTreesAndNetworks(Collection<TreeRecord> treeRecords, Collection<PhyloTree> networks) {
		clear();
		this.treeRecords.setAll(treeRecords);
		this.networks.setAll(networks);
		addTaxa(CollectionUtils.concatenate(treeRecords.stream().map(TreeRecord::getTree).filter(Objects::nonNull).toList(), networks));
	}

	public static void addTaxa(Collection<PhyloTree> list) {
		var labelIdMap = new TreeMap<String, Integer>();
		for (var tree : list) {
			for (var v : tree.nodes()) {
				tree.clearTaxa(v);
				if (v.isLeaf()) {
					tree.addTaxon(v, labelIdMap.computeIfAbsent(tree.getLabel(v), k -> labelIdMap.size() + 1));
				}
			}
		}
		if (false) {
			for (var entry : labelIdMap.entrySet()) {
				System.err.println(entry);
			}
		}
	}

	public static void addTaxa(PhyloTree source, Collection<PhyloTree> list) {
		var labelIdMap = new TreeMap<String, Integer>();
		for (var v : source.nodes()) {
			if (v.isLeaf()) {
				labelIdMap.put(source.getLabel(v), v.getId());
			}
		}

		for (var tree : list) {
			for (var v : tree.nodes()) {
				tree.clearTaxa(v);
				if (v.isLeaf()) {
					tree.addTaxon(v, labelIdMap.computeIfAbsent(tree.getLabel(v), k -> labelIdMap.size() + 1));
				}
			}
		}
		if (false) {
			for (var entry : labelIdMap.entrySet()) {
				System.err.println(entry);
			}
		}
	}

	public ReadOnlyBooleanProperty hasTreeRecordsProperty() {
		return hasTreeRecords;
	}

	public ReadOnlyBooleanProperty hasTreesProperty() {
		return hasTrees;
	}

	public boolean hasTrees() {
		return hasTreeRecords.get();
	}

	public boolean hasNetworks() {
		return hasNetworks.get();
	}

	public ReadOnlyBooleanProperty hasNetworksProperty() {
		return hasNetworks;
	}

	public BooleanProperty emptyProperty() {
		return empty;
	}

	public double getConfidenceThreshold() {
		return confidenceThreshold.get();
	}

	public DoubleProperty confidenceThresholdProperty() {
		return confidenceThreshold;
	}

	public List<PhyloTree> getRunTrees() {
		return treeRecords.stream().filter(TreeRecord::isRun).map(TreeRecord::getTree).filter(Objects::nonNull).toList();
	}

	public BitSet getShowTrees() {
		return BitSetUtils.asBitSet(treeRecords.stream().filter(TreeRecord::isShow).mapToInt(TreeRecord::getId).filter(Objects::nonNull).toArray());
	}

	public PhyloTree getNetwork() {
		if (networks.isEmpty())
			return null;
		else return networks.get(0);
	}
}
