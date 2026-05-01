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
	requires java.sql;
	requires org.apache.commons.numbers.gamma;
	requires jdk.compiler;
	requires com.install4j.runtime;

	exports phylofusion.main;
	opens phylofusion.main;

	exports phylofusion.window;
	opens phylofusion.window;
	opens phylofusion.algorithm;
	opens phylofusion.view;
	opens phylofusion.utils;
	opens phylofusion.trace;
}