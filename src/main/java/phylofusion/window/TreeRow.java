/*
 * TreeRow.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.window;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.phylo.PhyloTree;

public class TreeRow {
	private final StringProperty name = new SimpleStringProperty();
	private final BooleanProperty use = new SimpleBooleanProperty(false);
	private final BooleanProperty show = new SimpleBooleanProperty(false);
	private final PhyloTree tree;

	public TreeRow(String name, boolean use, boolean show, PhyloTree tree) {
		setName(name);
		setUse(use);
		setShow(show);
		this.tree = tree;
	}

	public StringProperty nameProperty() {
		return name;
	}

	public String getName() {
		return name.get();
	}

	public void setName(String v) {
		name.set(v);
	}

	public BooleanProperty useProperty() {
		return use;
	}

	public boolean isUse() {
		return use.get();
	}

	public void setUse(boolean v) {
		use.set(v);
	}

	public BooleanProperty showProperty() {
		return show;
	}

	public boolean isShow() {
		return show.get();
	}

	public void setShow(boolean v) {
		show.set(v);
	}

	public PhyloTree getTree() {
		return tree;
	}
}