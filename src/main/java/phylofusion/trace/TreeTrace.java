/*
 * TreeTrace.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.trace;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.CommentData;

import java.util.BitSet;

public class TreeTrace {

	public final static String KEY = "TT";

	public static BitSet getTT(Object nodeOrEdge) {
		if (nodeOrEdge instanceof Node v && v.getData() instanceof CommentData data) {
			return data.getIntSetValue(KEY).orElse(data.hasKey(KEY) ? new BitSet() : null);
		} else if (nodeOrEdge instanceof Edge e && e.getData() instanceof CommentData data) {
			return data.getIntSetValue(KEY).orElse(data.hasKey(KEY) ? new BitSet() : null);
		} else return null;
	}

	public static void setTT(Object nodeOrEdge) {
		setTT(nodeOrEdge, new BitSet());
	}

	public static void setTT(Object nodeOrEdge, BitSet set) {
		var commentData = new CommentData();
		commentData.put(KEY, set);
		if (nodeOrEdge instanceof Node v) {
			v.setData(commentData);
		} else if (nodeOrEdge instanceof Edge e) {
			e.setData(commentData);
		}
	}
}
