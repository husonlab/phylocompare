

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

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.phylo.PhyloTree;

public class Document {
	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final ObservableList<PhyloTree> networks = FXCollections.observableArrayList();
	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", false);

	public Document() {
		empty.bind(Bindings.isEmpty(trees));
	}

	public boolean isEmpty() {
		return empty.get();
	}

	public BooleanProperty emptyProperty() {
		return empty;
	}

	public ObservableList<PhyloTree> getTrees() {
		return trees;
	}

	public ObservableList<PhyloTree> getNetworks() {
		return networks;
	}
}
