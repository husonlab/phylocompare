/*
 * PhyloParallelogramsText.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.fx.options.OptionsRegistry;
import jloda.fx.util.ColorUtilsFX;
import jloda.phylo.CommentData;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.FileUtils;
import phyloparallelograms.model.Document;
import phyloparallelograms.window.TreeRecord;
import splitstree6.data.TaxaBlock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * reads and writes a PhyloParallelograms document to and from a plain text file.
 * <p>
 * This is a text-based, pure-Java replacement for the SQLite-based {@link PhyloParallelogramsDB}, and mirrors its
 * behaviour exactly, so that it can be used interchangeably on platforms (such as iOS) where the native SQLite driver
 * is not available. Both persist the same content: taxa, trees (with layout flags and colors), networks, and the
 * annotated options registry.
 * <p>
 * The format is line-oriented and self-describing. It opens with a magic header line, followed by BEGIN/END sections.
 * Within a section, each record is one line of tab-separated, escaped fields:
 * <pre>
 *   #PhyloParallelograms 1.0
 *   BEGIN metadata;
 *   	&lt;key&gt;	&lt;value&gt;
 *   END;
 *   BEGIN taxa;
 *   	&lt;id&gt;	&lt;name&gt;	&lt;display_label&gt;
 *   END;
 *   BEGIN trees;
 *   	&lt;id&gt;	&lt;name&gt;	&lt;run 0|1&gt;	&lt;show 0|1&gt;	&lt;color|&gt;	&lt;newick&gt;
 *   END;
 *   BEGIN networks;
 *   	&lt;id&gt;	&lt;name&gt;	&lt;newick&gt;
 *   END;
 *   BEGIN parameters;
 *   	&lt;name&gt;	&lt;type&gt;	&lt;value&gt;	&lt;description&gt;	&lt;legal_range&gt;
 *   END;
 * </pre>
 * Tab, newline, carriage-return and backslash are backslash-escaped in every field, so each record occupies exactly
 * one physical line and fields split unambiguously on the raw tab character.
 * <p>
 * Daniel Huson, 7.2026
 */
public class PhyloParallelogramsText {

	public static final String MAGIC = "#PhyloParallelograms";
	public static final String VERSION = "1.0";

	private static final String SECTION_METADATA = "metadata";
	private static final String SECTION_TAXA = "taxa";
	private static final String SECTION_TREES = "trees";
	private static final String SECTION_NETWORKS = "networks";
	private static final String SECTION_PARAMETERS = "parameters";

	private static final String METADATA_NOTE = "note";

	/**
	 * writes the document content to a text file, mirroring {@link PhyloParallelogramsDB#save}
	 */
	public static void save(String fileName, List<TreeRecord> treeRecords, List<PhyloTree> networks,
							TaxaBlock taxaBlock, OptionsRegistry options, String note) throws IOException {

		var newickIO = new NewickIO();
		newickIO.setNewickNodeCommentSupplier(CommentData.createDataNodeSupplier());
		newickIO.setNewickEdgeCommentSupplier(CommentData.createDataEdgeSupplier());

		try (var w = Files.newBufferedWriter(Path.of(fileName), StandardCharsets.UTF_8)) {
			writeLine(w, MAGIC + " " + VERSION);

			// metadata (key-value; only written when non-empty, so older files simply omit the section)
			beginSection(w, SECTION_METADATA);
			if (note != null && !note.isEmpty())
				writeRecord(w, METADATA_NOTE, note);
			endSection(w);

			// taxa
			beginSection(w, SECTION_TAXA);
			for (var t = 1; t <= taxaBlock.getNtax(); t++) {
				var taxon = taxaBlock.get(t);
				writeRecord(w, String.valueOf(t), taxon.getName(), taxon.getDisplayLabelOrName());
			}
			endSection(w);

			// trees
			beginSection(w, SECTION_TREES);
			for (var record : treeRecords) {
				var color = record.getColor() == null ? "" : ColorUtilsFX.toWeb(record.getColor());
				var newick = record.getTree() == null ? "" : newickIO.toBracketString(record.getTree(), true) + ";";
				writeRecord(w, String.valueOf(record.getId()), record.getName(),
						record.getRunLayout() ? "1" : "0", record.isShow() ? "1" : "0", color, newick);
			}
			endSection(w);

			// networks
			beginSection(w, SECTION_NETWORKS);
			for (var i = 0; i < networks.size(); i++) {
				var network = networks.get(i);
				writeRecord(w, String.valueOf(i), network.getName(), newickIO.toBracketString(network, true) + ";");
			}
			endSection(w);

			// parameters (annotated options; only name+value are read back, the rest aid inspection)
			beginSection(w, SECTION_PARAMETERS);
			if (options != null) {
				for (var item : options.getItems()) {
					if (!item.isPersist())
						continue;
					writeRecord(w, item.getName(), item.getValueType().toString().toLowerCase(), item.getValueString());
				}
			}
			endSection(w);
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Error saving file: " + fileName, e);
		}
	}

	/**
	 * loads the document content from a text file, mirroring {@link PhyloParallelogramsDB#load}
	 */
	public static void load(String fileName, Document document, OptionsRegistry options) throws IOException {
		FileUtils.checkFileReadableNonEmpty(fileName);
		if (!isPhyloParallelogramsTextFile(fileName))
			throw new IOException("Not a PhyloParallelograms file");

		document.clear();

		var treeRecords = new ArrayList<TreeRecord>();
		var networks = new TreeMap<Integer, PhyloTree>();
		var taxaBlock = new TaxaBlock();
		// collected while parsing, but applied only at the end: addTreesAndNetworks() below calls
		// document.clear(), which would otherwise wipe a note set during parsing
		var note = "";

		var networkNewickIO = new NewickIO();
		networkNewickIO.setNewickNodeCommentConsumer(CommentData.createDataNodeConsumer());
		networkNewickIO.setNewickEdgeCommentConsumer(CommentData.createDataEdgeConsumer());

		try (var reader = new BufferedReader(FileUtils.getReaderPossiblyZIPorGZIP(fileName))) {
			String section = null;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith(MAGIC))
					continue;
				var trimmed = line.strip();
				if (trimmed.startsWith("BEGIN ") && trimmed.endsWith(";")) {
					section = trimmed.substring("BEGIN ".length(), trimmed.length() - 1).strip();
					continue;
				}
				if (trimmed.equals("END;")) {
					section = null;
					continue;
				}
				if (section == null)
					continue;

				// NB: do not strip() the data line - a trailing tab denotes an empty final field (e.g. an empty
				// newick for a tree without a tree) that strip() would silently drop
				var fields = decodeFields(line);
				switch (section) {
					case SECTION_METADATA -> {
						if (fields.length >= 2 && fields[0].equals(METADATA_NOTE))
							note = fields[1];
					}
					case SECTION_TAXA -> {
						if (fields.length >= 3) {
							var name = fields[1];
							var displayLabel = fields[2];
							taxaBlock.addTaxonByName(name);
							var taxon = taxaBlock.get(taxaBlock.size());
							taxon.setDisplayLabel(displayLabel);
						}
					}
					case SECTION_TREES -> {
						if (fields.length >= 6) {
							var id = parseIntOrZero(fields[0]);
							var name = fields[1];
							var run = fields[2].equals("1");
							var show = fields[3].equals("1");
							var colorString = fields[4];
							var newick = fields[5];

							PhyloTree tree;
							if (!newick.isBlank()) {
								tree = new PhyloTree();
								tree.parseBracketNotation(newick, true);
								if (!name.isBlank())
									tree.setName(name);
								if (tree.getName() == null || tree.getName().isBlank())
									tree.setName("tree-" + id);
							} else tree = null;

							var record = new TreeRecord(name, id, run, show, tree);
							if (ColorUtilsFX.isColor(colorString))
								record.setColor(ColorUtilsFX.parseColor(colorString));
							treeRecords.add(record);
						}
					}
					case SECTION_NETWORKS -> {
						if (fields.length >= 3) {
							var id = parseIntOrZero(fields[0]);
							var name = fields[1];
							var newick = fields[2];
							var network = new PhyloTree();
							networkNewickIO.parseBracketNotation(network, newick, true);
							if (!name.isBlank())
								network.setName(name);
							if (network.getName() == null || network.getName().isBlank())
								network.setName("network-" + id);
							networks.put(id, network);
						}
					}
					case SECTION_PARAMETERS -> {
						if (options != null && fields.length >= 3) {
							var name = fields[0];
							var value = fields[2];
							if (!name.isEmpty())
								options.setValueString(name, value);
						}
					}
					default -> {
					}
				}
			}
		}

		if (!treeRecords.isEmpty())
			document.addTreesAndNetworks(treeRecords, networks.values());
		else if (!networks.isEmpty())
			document.addNetworks(networks.values());
		if (taxaBlock.size() > 0) {
			document.getTaxaBlock().clear();
			taxaBlock.getTaxa().forEach(t -> document.getTaxaBlock().add(t));
		}
		// set last, as addTreesAndNetworks()/addNetworks() above clear the document, including its note
		document.setNote(note);
	}

	/**
	 * checks whether the named file looks like a PhyloParallelograms text file, mirroring
	 * {@link SQLiteUtils#isSQLiteWithTreesOrNetworksTable}
	 */
	public static boolean isPhyloParallelogramsTextFile(String fileName) {
		if (fileName == null)
			return false;
		try (var reader = new BufferedReader(FileUtils.getReaderPossiblyZIPorGZIP(fileName))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank())
					continue;
				return line.startsWith(MAGIC);
			}
		} catch (Exception ex) {
			return false;
		}
		return false;
	}

	private static int parseIntOrZero(String s) {
		try {
			return Integer.parseInt(s.strip());
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static void beginSection(BufferedWriter w, String name) throws IOException {
		writeLine(w, "BEGIN " + name + ";");
	}

	private static void endSection(BufferedWriter w) throws IOException {
		writeLine(w, "END;");
	}

	private static void writeRecord(BufferedWriter w, String... fields) throws IOException {
		var buf = new StringBuilder("\t");
		for (var i = 0; i < fields.length; i++) {
			if (i > 0)
				buf.append('\t');
			buf.append(encode(fields[i]));
		}
		writeLine(w, buf.toString());
	}

	private static void writeLine(BufferedWriter w, String line) throws IOException {
		w.write(line);
		w.write('\n');
	}

	/**
	 * escapes a single field so that it contains no raw tab, newline, carriage-return or backslash
	 */
	private static String encode(String s) {
		if (s == null)
			return "";
		var buf = new StringBuilder(s.length());
		for (var i = 0; i < s.length(); i++) {
			var ch = s.charAt(i);
			switch (ch) {
				case '\\' -> buf.append("\\\\");
				case '\t' -> buf.append("\\t");
				case '\n' -> buf.append("\\n");
				case '\r' -> buf.append("\\r");
				default -> buf.append(ch);
			}
		}
		return buf.toString();
	}

	/**
	 * splits a record line on raw tabs and unescapes each field; the record has a leading tab, yielding an empty
	 * first token that is dropped
	 */
	static String[] decodeFields(String line) {
		var raw = line.split("\t", -1);
		var start = (raw.length > 0 && raw[0].isEmpty()) ? 1 : 0;
		var result = new String[raw.length - start];
		for (var i = start; i < raw.length; i++)
			result[i - start] = decode(raw[i]);
		return result;
	}

	private static String decode(String s) {
		if (s.indexOf('\\') < 0)
			return s;
		var buf = new StringBuilder(s.length());
		for (var i = 0; i < s.length(); i++) {
			var ch = s.charAt(i);
			if (ch == '\\' && i + 1 < s.length()) {
				var next = s.charAt(++i);
				switch (next) {
					case '\\' -> buf.append('\\');
					case 't' -> buf.append('\t');
					case 'n' -> buf.append('\n');
					case 'r' -> buf.append('\r');
					default -> buf.append(next);
				}
			} else buf.append(ch);
		}
		return buf.toString();
	}
}
