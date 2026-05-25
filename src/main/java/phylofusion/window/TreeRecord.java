/*
 * TreeRecord.java Copyright (C) 2026 Daniel H. Huson
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

public class TreeRecord {
	private final StringProperty name = new SimpleStringProperty();
	private final int id;
	private final BooleanProperty run = new SimpleBooleanProperty(false);
	private final BooleanProperty show = new SimpleBooleanProperty(false);
	private final PhyloTree tree;

	public TreeRecord(String name, int id, boolean run, boolean show, PhyloTree tree) {
		setName(name);
		this.id = id;
		setRun(run);
		setShow(show);
		this.runProperty().addListener((v, o, n) -> {
			if (n)
				setShow(true);
		});
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

	public BooleanProperty runProperty() {
		return run;
	}

	public boolean isRun() {
		return run.get();
	}

	public void setRun(boolean v) {
		run.set(v);
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

	public int getId() {
		return id;
	}
}