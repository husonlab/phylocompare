module phyloparallelograms {
	uses jloda.fx.service.UpdateService;

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
	requires java.desktop;
	requires java.net.http;
	requires com.fasterxml.jackson.databind;
	requires ojalgo;

	exports phyloparallelograms.main;
	exports phyloparallelograms.window;
	exports phyloparallelograms.view;
	exports phyloparallelograms.model;
	exports phyloparallelograms.io;

	opens phyloparallelograms.main;
	opens phyloparallelograms.window;
	opens phyloparallelograms.algorithm;
	opens phyloparallelograms.view;
	opens phyloparallelograms.utils;
	opens phyloparallelograms.trace;
	opens phyloparallelograms.model;

	opens phyloparallelograms.io;

	opens phyloparallelograms.examples;
	exports phyloparallelograms.examples;
}