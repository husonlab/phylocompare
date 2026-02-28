/*
 * DoubleSpinnerBinder.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.beans.property.DoubleProperty;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.StringConverter;

public final class DoubleSpinnerBinder {

	private DoubleSpinnerBinder() {
	}

	/**
	 * Configure an existing Spinner<Double> and attach it bidirectionally to the given DoubleProperty.
	 * <p>
	 * Rules:
	 * - value is clamped to [min, max]
	 * - editor commits on Enter and focus loss
	 * - property <-> spinner stay in sync without feedback loops
	 */
	public static void setupAndBind(Spinner<Double> spinner,
									DoubleProperty property,
									double min,
									double max,
									double initial,
									double step) {

		spinner.setEditable(true);

		var vf = new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step);

		vf.setConverter(new StringConverter<>() {
			@Override
			public String toString(Double v) {
				if (v == null) return "";
				double d = v;
				return (d == (long) d) ? Long.toString((long) d) : Double.toString(d);
			}

			@Override
			public Double fromString(String s) {
				if (s == null) return vf.getValue();
				s = s.trim();
				if (s.isEmpty()) return vf.getValue();
				try {
					double v = Double.parseDouble(s);
					return clamp(v, min, max);
				} catch (NumberFormatException ex) {
					return vf.getValue();
				}
			}
		});

		spinner.setValueFactory(vf);

		// commit on Enter and focus loss
		spinner.getEditor().setOnAction(e -> commitEditorText(spinner));
		spinner.getEditor().focusedProperty().addListener((obs, was, is) -> {
			if (!is) commitEditorText(spinner);
		});

		// initialize from property if it already has a value you want to honor
		vf.setValue(clamp(property.get(), min, max));

		// two-way sync (boxed Double <-> primitive double)
		vf.valueProperty().addListener((obs, oldV, newV) -> {
			if (newV == null) return;
			double v = clamp(newV, min, max);
			if (Double.compare(v, property.get()) != 0) {
				property.set(v);
			}
		});

		property.addListener((obs, oldV, newV) -> {
			double v = clamp(newV.doubleValue(), min, max);
			Double cur = vf.getValue();
			if (cur == null || Double.compare(v, cur) != 0) {
				vf.setValue(v);
			}
		});
	}

	/**
	 * Convenience: non-negative doubles with default 30.
	 */
	public static void setupAndBindNonNegative(Spinner<Double> spinner, DoubleProperty property) {
		setupAndBind(spinner, property, 0.0, 1e9, 30.0, 1.0);
	}

	private static void commitEditorText(Spinner<Double> spinner) {
		var vf = spinner.getValueFactory();
		if (vf == null) return;
		String text = spinner.getEditor().getText();
		try {
			Double value = ((SpinnerValueFactory<Double>) vf).getConverter().fromString(text);
			vf.setValue(value);
		} catch (Exception ignored) {
			spinner.getEditor().setText(((SpinnerValueFactory<Double>) vf).getConverter().toString(vf.getValue()));
		}
	}

	private static double clamp(double v, double min, double max) {
		if (v < min) return min;
		if (v > max) return max;
		return v;
	}
}