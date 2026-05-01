/*
 * ChooseColorSchemeSetup.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.fx.util.ColorSchemeManager;
import phylofusion.window.MainWindow;

public class ChooseColorSchemeSetup {
	public static void apply(MainWindow window) {
		var cbox = window.getController().getColorSchemeCBox();
		cbox.setPrefWidth(48);
		cbox.setMinWidth(48);
		cbox.setMaxWidth(48);

		cbox.setCellFactory(cb -> new javafx.scene.control.ListCell<>() {
			private final javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();

			@Override
			protected void updateItem(String name, boolean empty) {
				super.updateItem(name, empty);

				if (empty || name == null) {
					setGraphic(null);
					setText(null);
				} else {
					imageView.setImage(ColorSchemeManager.getInstance().createIcon(name));
					imageView.setFitWidth(16);
					imageView.setFitHeight(16);

					setGraphic(imageView);
					setText(null);
				}
			}
		});

		cbox.setButtonCell(new javafx.scene.control.ListCell<>() {
			private final javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();

			@Override
			protected void updateItem(String name, boolean empty) {
				super.updateItem(name, empty);

				if (empty || name == null) {
					setGraphic(null);
					setText(null);
				} else {
					imageView.setImage(ColorSchemeManager.getInstance().createIcon(name));
					imageView.setFitWidth(16);
					imageView.setFitHeight(16);

					setGraphic(imageView);
					setText(null); // <- critical: no text here
				}
			}
		});
		cbox.valueProperty().addListener((v, o, n) -> {
			if (n != null && ColorSchemeManager.getInstance().getNames().contains(n)) {
				window.getDocument().setColorSchemeName(n);
			}
		});

		cbox.getItems().addAll("Caspian8", "Fews8", "Glasbey29", "Pairs12", "Retro29", "Twenty");
		cbox.setValue(window.getDocument().getColorSchemeName());
	}


}
