/*
 * TransformNodesEdges.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.shape.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;


public class TransformNodesEdges {
	public static void apply(Collection<? extends Node> nodes, Collection<Path> edgePaths, BiFunction<Double, Double, Point2D> transform) {
		for (var node : nodes) {
			var point = transform.apply(node.getTranslateX(), node.getTranslateY());
			node.setTranslateX(point.getX());
			node.setTranslateY(point.getY());
		}
		for (var path : edgePaths) {
			var elements = transformElements(path.getElements(), transform);
			path.getElements().setAll(elements);
		}
	}

	public static List<PathElement> transformElements(
			Iterable<PathElement> elements,
			BiFunction<Double, Double, Point2D> transform) {

		var out = new ArrayList<PathElement>();
		double curX = 0, curY = 0, startX = 0, startY = 0;   // original coords

		for (var element : elements) {
			if (element instanceof MoveTo e) {
				var p = transform.apply(e.getX(), e.getY());
				out.add(new MoveTo(p.getX(), p.getY()));
				curX = startX = e.getX();
				curY = startY = e.getY();
			} else if (element instanceof LineTo e) {
				var p = transform.apply(e.getX(), e.getY());
				out.add(new LineTo(p.getX(), p.getY()));
				curX = e.getX();
				curY = e.getY();
			} else if (element instanceof HLineTo e) {          // implied (x, curY)
				var p = transform.apply(e.getX(), curY);
				out.add(new LineTo(p.getX(), p.getY()));
				curX = e.getX();
			} else if (element instanceof VLineTo e) {          // implied (curX, y)
				var p = transform.apply(curX, e.getY());
				out.add(new LineTo(p.getX(), p.getY()));
				curY = e.getY();
			} else if (element instanceof QuadCurveTo e) {
				var c = transform.apply(e.getControlX(), e.getControlY());
				var p = transform.apply(e.getX(), e.getY());
				out.add(new QuadCurveTo(c.getX(), c.getY(), p.getX(), p.getY()));
				curX = e.getX();
				curY = e.getY();
			} else if (element instanceof CubicCurveTo e) {
				var c1 = transform.apply(e.getControlX1(), e.getControlY1());
				var c2 = transform.apply(e.getControlX2(), e.getControlY2());
				var p = transform.apply(e.getX(), e.getY());
				out.add(new CubicCurveTo(c1.getX(), c1.getY(), c2.getX(), c2.getY(),
						p.getX(), p.getY()));
				curX = e.getX();
				curY = e.getY();
			} else if (element instanceof ArcTo e) {            // correct only for translation
				var p = transform.apply(e.getX(), e.getY());
				out.add(new ArcTo(e.getRadiusX(), e.getRadiusY(), e.getXAxisRotation(),
						p.getX(), p.getY(), e.isLargeArcFlag(), e.isSweepFlag()));
				curX = e.getX();
				curY = e.getY();
			} else if (element instanceof ClosePath) {
				out.add(new ClosePath());
				curX = startX;
				curY = startY;
			} else {
				throw new IllegalArgumentException("Unsupported: " + element.getClass().getSimpleName());
			}
		}
		return out;
	}
}
