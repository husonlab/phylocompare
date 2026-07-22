/*
 * PhyloParallelogramsTextTest.java Copyright (C) 2026 Daniel H. Huson
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

package phyloparallelograms.io;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;
import jloda.fx.options.Option;
import jloda.fx.options.OptionsRegistry;
import jloda.fx.util.ColorUtilsFX;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import phyloparallelograms.model.Document;
import phyloparallelograms.window.TreeRecord;
import splitstree6.data.TaxaBlock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * round-trip test for {@link PhyloParallelogramsText}: verifies that saving and loading via the text format preserves
 * all document content, that repeated serialization is stable, and that the text format is equivalent to the SQLite
 * format {@link PhyloParallelogramsDB} (which requires the native driver, present on desktop).
 * <p>
 * This is a standalone runnable test (the repo has no JUnit); a non-zero exit code signals failure. It runs on the
 * JavaFX application thread because {@link Document} installs listeners that use {@code Platform.runLater}.
 * <p>
 * Daniel Huson, 7.2026
 */
public class PhyloParallelogramsTextTest {
	private static int failures = 0;
	private static int checks = 0;

	public static void main(String[] args) throws Exception {
		Platform.startup(() -> {
		});
		var latch = new CountDownLatch(1);
		try {
			Platform.runLater(() -> {
				try {
					runAll();
				} catch (Throwable t) {
					t.printStackTrace();
					failures++;
				} finally {
					latch.countDown();
				}
			});
			latch.await();
		} finally {
			Platform.exit();
		}
		System.out.printf("%n%d checks, %d failure(s)%n", checks, failures);
		System.exit(failures == 0 ? 0 : 1);
	}

	private static void runAll() throws Exception {
		// -------- build inputs (raw, as save() expects) --------
		var taxaBlock = new TaxaBlock();
		var names = new String[]{"A", "B", "C", "D"};
		var displayLabels = new String[]{"Alpha", "Bravo", "Charlie", "Delta"};
		for (var i = 0; i < names.length; i++) {
			taxaBlock.addTaxonByName(names[i]);
			taxaBlock.get(taxaBlock.size()).setDisplayLabel(displayLabels[i]);
		}

		var treeRecords = new ArrayList<TreeRecord>();
		treeRecords.add(makeTreeRecord("first tree", 0, true, true, "((A,B),(C,D));", "#e41a1c"));
		// awkward but legal name to exercise field escaping
		treeRecords.add(makeTreeRecord("tab\there\tname", 1, false, true, "(A,(B,(C,D)));", "#377eb8"));
		treeRecords.add(makeTreeRecord("third tree", 2, false, false, "(A,B,(C,D));", "#4daf4a"));
		// a record with no tree (empty newick): exercises the Document null-tree guard end-to-end
		var noTree = new TreeRecord("no tree", 3, false, false, null);
		noTree.setColor(Color.web("#984ea3"));
		treeRecords.add(noTree);

		var networks = new ArrayList<PhyloTree>();
		networks.add(makeNetwork("net one", "((A,B),(C,D));"));
		networks.add(makeNetwork("net two", "(A,(B,C),D);"));

		var inCarrier = new OptCarrier();
		inCarrier.alpha.set("value with\ttab and \\ backslash");
		inCarrier.beta.set(42.5);
		var inOptions = OptionsRegistry.of(inCarrier);

		// -------- Test 0: field encoding, incl. empty trailing field (Document-free) --------
		var decoded = PhyloParallelogramsText.decodeFields("\tone\ttwo\t");
		eq(3, decoded.length, "decodeFields: trailing empty field is preserved (not dropped)");
		eq("", decoded[2], "decodeFields: trailing field decodes to empty string");
		eq("two", decoded[1], "decodeFields: middle field");

		// a record whose (last) newick field is empty must still serialize+reparse with all 6 fields
		var emptyNewickRecord = new TreeRecord("no tree", 7, false, false, null);
		emptyNewickRecord.setColor(Color.web("#984ea3"));
		var soloFile = Files.createTempFile("phypar-solo-", ".phypar");
		PhyloParallelogramsText.save(soloFile.toString(), List.of(emptyNewickRecord), List.of(),
				new TaxaBlock(), null, "");
		var treesLine = Files.readAllLines(soloFile, StandardCharsets.UTF_8).stream()
				.filter(l -> l.startsWith("\t")).findFirst().orElse("");
		var soloFields = PhyloParallelogramsText.decodeFields(treesLine);
		eq(6, soloFields.length, "empty-newick record serializes with all 6 fields");
		eq("", soloFields[5], "empty-newick record: newick field is empty");
		eq("no tree", soloFields[1], "empty-newick record: name field intact");

		// -------- Test 1: text save -> load preserves content --------
		// a note with a tab and a newline, to exercise field escaping in the metadata section
		var inNote = "From Zhang et al. 2025\tJSE\nDOI: 10.1111/jse.12345";
		var textFile = Files.createTempFile("phypar-text-", ".phypar");
		PhyloParallelogramsText.save(textFile.toString(), treeRecords, networks, taxaBlock, inOptions, inNote);

		check(PhyloParallelogramsText.isPhyloParallelogramsTextFile(textFile.toString()),
				"detection: text file recognized");
		check(!looksLikeSQLite(textFile), "text file is not SQLite");

		var textDoc = new Document();
		var textCarrier = new OptCarrier();
		var textOptions = OptionsRegistry.of(textCarrier);
		PhyloParallelogramsText.load(textFile.toString(), textDoc, textOptions);

		compareContent("text round-trip", treeRecords, networks, taxaBlock, inCarrier, textDoc, textCarrier);
		eq(inNote, textDoc.getNote(), "note round-trips through save/load, incl. tab and newline");

		// -------- Test 2: serialization is idempotent (re-save equals first save) --------
		var textFile2 = Files.createTempFile("phypar-text2-", ".phypar");
		PhyloParallelogramsText.save(textFile2.toString(), new ArrayList<>(textDoc.getTreeRecords()),
				new ArrayList<>(textDoc.getNetworks()), textDoc.getTaxaBlock(), textOptions, textDoc.getNote());
		eq(Files.readString(textFile, StandardCharsets.UTF_8), Files.readString(textFile2, StandardCharsets.UTF_8),
				"idempotence: second save byte-identical to first");

		// -------- Test 3: text format equivalent to SQLite format --------
		try {
			var dbFile = Files.createTempFile("phypar-db-", ".phypar");
			Files.deleteIfExists(dbFile); // let SQLite create it fresh
			var dbCarrierIn = new OptCarrier();
			dbCarrierIn.alpha.set(inCarrier.alpha.get());
			dbCarrierIn.beta.set(inCarrier.beta.get());
			PhyloParallelogramsDB.save(dbFile.toString(), treeRecords, networks, taxaBlock,
					OptionsRegistry.of(dbCarrierIn));

			var dbDoc = new Document();
			var dbCarrier = new OptCarrier();
			var dbOptions = OptionsRegistry.of(dbCarrier);
			PhyloParallelogramsDB.load(dbFile.toString(), dbDoc, dbOptions);

			compareContent("sqlite round-trip", treeRecords, networks, taxaBlock, inCarrier, dbDoc, dbCarrier);
			// and text-loaded == sqlite-loaded
			compareDocuments("text vs sqlite", textDoc, dbDoc);
		} catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
			System.out.println("skip: SQLite equivalence (native driver unavailable): " + ex.getMessage());
		}
	}

	// ---------- comparisons ----------

	private static void compareContent(String label, List<TreeRecord> inRecords, List<PhyloTree> inNetworks,
									   TaxaBlock inTaxa, OptCarrier inCarrier, Document doc, OptCarrier carrier) {
		// taxa
		eq(inTaxa.getNtax(), doc.getTaxaBlock().getNtax(), label + ": taxa count");
		for (var t = 1; t <= Math.min(inTaxa.getNtax(), doc.getTaxaBlock().getNtax()); t++) {
			eq(inTaxa.get(t).getName(), doc.getTaxaBlock().get(t).getName(), label + ": taxon " + t + " name");
			eq(inTaxa.get(t).getDisplayLabelOrName(), doc.getTaxaBlock().get(t).getDisplayLabelOrName(),
					label + ": taxon " + t + " display label");
		}

		// trees
		eq(inRecords.size(), doc.getTreeRecords().size(), label + ": tree count");
		for (var i = 0; i < Math.min(inRecords.size(), doc.getTreeRecords().size()); i++) {
			var a = inRecords.get(i);
			var b = doc.getTreeRecords().get(i);
			eq(a.getId(), b.getId(), label + ": tree[" + i + "] id");
			eq(a.getName(), b.getName(), label + ": tree[" + i + "] name");
			eq(a.getRunLayout(), b.getRunLayout(), label + ": tree[" + i + "] run");
			eq(a.isShow(), b.isShow(), label + ": tree[" + i + "] show");
			eq(web(a.getColor()), web(b.getColor()), label + ": tree[" + i + "] color");
			eq(nwk(a.getTree()), nwk(b.getTree()), label + ": tree[" + i + "] newick");
		}

		// networks
		eq(inNetworks.size(), doc.getNetworks().size(), label + ": network count");
		for (var i = 0; i < Math.min(inNetworks.size(), doc.getNetworks().size()); i++) {
			eq(inNetworks.get(i).getName(), doc.getNetworks().get(i).getName(), label + ": network[" + i + "] name");
			eq(nwk(inNetworks.get(i)), nwk(doc.getNetworks().get(i)), label + ": network[" + i + "] newick");
		}

		// options
		eq(inCarrier.alpha.get(), carrier.alpha.get(), label + ": option alpha");
		eq(inCarrier.beta.get(), carrier.beta.get(), label + ": option beta");
	}

	private static void compareDocuments(String label, Document a, Document b) {
		eq(a.getTaxaBlock().getNtax(), b.getTaxaBlock().getNtax(), label + ": taxa count");
		eq(a.getTreeRecords().size(), b.getTreeRecords().size(), label + ": tree count");
		for (var i = 0; i < Math.min(a.getTreeRecords().size(), b.getTreeRecords().size()); i++) {
			eq(nwk(a.getTreeRecords().get(i).getTree()), nwk(b.getTreeRecords().get(i).getTree()),
					label + ": tree[" + i + "] newick");
			eq(web(a.getTreeRecords().get(i).getColor()), web(b.getTreeRecords().get(i).getColor()),
					label + ": tree[" + i + "] color");
		}
		eq(a.getNetworks().size(), b.getNetworks().size(), label + ": network count");
		for (var i = 0; i < Math.min(a.getNetworks().size(), b.getNetworks().size()); i++)
			eq(nwk(a.getNetworks().get(i)), nwk(b.getNetworks().get(i)), label + ": network[" + i + "] newick");
	}

	// ---------- helpers ----------

	private static TreeRecord makeTreeRecord(String name, int id, boolean run, boolean show, String newick, String color)
			throws java.io.IOException {
		var tree = new PhyloTree();
		tree.parseBracketNotation(newick, true);
		tree.setName(name);
		var record = new TreeRecord(name, id, run, show, tree);
		record.setColor(Color.web(color));
		return record;
	}

	private static PhyloTree makeNetwork(String name, String newick) throws java.io.IOException {
		var network = new PhyloTree();
		network.parseBracketNotation(newick, true);
		network.setName(name);
		return network;
	}

	private static String nwk(PhyloTree tree) {
		if (tree == null)
			return null;
		return new NewickIO().toBracketString(tree, true);
	}

	private static String web(Color color) {
		return color == null ? null : ColorUtilsFX.toWeb(color);
	}

	private static boolean looksLikeSQLite(Path file) {
		try (var raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
			var header = new byte[16];
			raf.readFully(header);
			return new String(header, StandardCharsets.UTF_8).startsWith("SQLite format 3");
		} catch (Exception ex) {
			return false;
		}
	}

	private static void eq(Object expected, Object actual, String msg) {
		check(Objects.equals(expected, actual), msg + " (expected=<" + expected + ">, actual=<" + actual + ">)");
	}

	private static void check(boolean condition, String msg) {
		checks++;
		if (condition) {
			System.out.println("ok:   " + msg);
		} else {
			failures++;
			System.out.println("FAIL: " + msg);
		}
	}

	/**
	 * a small options carrier, independent of the global ProgramProperties store, so the options round-trip can be
	 * verified in isolation
	 */
	public static class OptCarrier {
		@Option(description = "a string option")
		private final StringProperty alpha = new SimpleStringProperty("default-alpha");

		@Option(description = "a numeric option", min = 0, max = 100)
		private final DoubleProperty beta = new SimpleDoubleProperty(0);
	}
}
