module phylofusion {
	requires javafx.base;
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.graphics;
	requires jloda_core;
	requires jloda_fx;
	requires jloda_phylogeny;
	requires splitstreesix;
	requires java.xml;

	exports phylofusion.main;
	opens phylofusion.window;
	opens phylofusion.algorithm;
	opens phylofusion.view;
	opens phylofusion.utils;
	opens phylofusion.trace;
}