/*
 * SplitPaneSupport.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.utils;

import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import jloda.fx.util.RunThrottled;
import jloda.util.Single;

public class SplitPaneSupport {
	public static void installKeepLeftSameDuringWindowResize(Region root, SplitPane splitPane) {
		var oldWidth = new Single<Double>();
		root.widthProperty().addListener((v, o, n) -> {
			if (o.doubleValue() > 0 && n.doubleValue() > 0) {
				oldWidth.setIfCurrentValueIsNull(o.doubleValue() * splitPane.getDividerPositions()[0]);
				RunThrottled.apply(splitPane, () -> {
					var newPos = oldWidth.get() / n.doubleValue();
					splitPane.setDividerPosition(0, newPos);
					oldWidth.set(null);
				}, 2);
			}
		});
	}
}
