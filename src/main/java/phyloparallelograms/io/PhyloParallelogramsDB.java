/*
 * PhyloParallelogramsDB.java Copyright (C) 2026 Daniel H. Huson
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
import jloda.fx.options.OptionsSQL;
import jloda.fx.util.ColorUtilsFX;
import jloda.phylo.CommentData;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.FileUtils;
import phyloparallelograms.model.Document;
import phyloparallelograms.window.TreeRecord;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class PhyloParallelogramsDB {

	public static void save(String fileName, List<TreeRecord> treeRecords, List<PhyloTree> networks,
							TaxaBlock taxaBlock, OptionsRegistry options) throws IOException {
		var url = "jdbc:sqlite:" + fileName;

		try (var conn = DriverManager.getConnection(url)) {
			conn.setAutoCommit(false);
			try {
				try (var stmt = conn.createStatement()) {
					stmt.execute("PRAGMA foreign_keys = ON;");

					// Ensure schema exists
					stmt.execute("""
								CREATE TABLE IF NOT EXISTS trees (
									id      INTEGER PRIMARY KEY,
									name    TEXT NOT NULL,
									run     INTEGER NOT NULL CHECK (run IN (0,1)),
									show    INTEGER NOT NULL CHECK (show IN (0,1)),
									color   TEXT,
									newick  TEXT NOT NULL
								);
							""");

					stmt.execute("""
								CREATE TABLE IF NOT EXISTS networks (
									id      INTEGER PRIMARY KEY,
									name    TEXT NOT NULL,
									newick  TEXT NOT NULL
								);
							""");

					stmt.execute("""
								CREATE TABLE IF NOT EXISTS taxa (
									id      INTEGER PRIMARY KEY,
									name   TEXT NOT NULL,
									display_label TEXT
								);
							""");

					// Clear old contents
					stmt.executeUpdate("DELETE FROM trees");
					stmt.executeUpdate("DELETE FROM networks");
					stmt.executeUpdate("DELETE FROM taxa");
				}

				var newickIO = new NewickIO();
				newickIO.setNewickNodeCommentSupplier(CommentData.createDataNodeSupplier());
				newickIO.setNewickEdgeCommentSupplier(CommentData.createDataEdgeSupplier());


				try (var ps = conn.prepareStatement(
						"INSERT INTO trees (id, name, run, show, color, newick) VALUES (?, ?, ?, ?, ?, ?)")) {
					for (var record : treeRecords) {
						ps.setInt(1, record.getId());
						ps.setString(2, record.getName());
						ps.setInt(3, record.getRunLayout() ? 1 : 0);
						ps.setInt(4, record.isShow() ? 1 : 0);
						if (record.getColor() != null) {
							ps.setString(5, ColorUtilsFX.toWeb(record.getColor()));
						} else
							ps.setString(5, null);
						var newick = record.getTree() == null ? "" : newickIO.toBracketString(record.getTree(), true) + ";";
						ps.setString(6, newick);
						ps.addBatch();
					}
					ps.executeBatch();
				}

				try (var ps = conn.prepareStatement("INSERT INTO networks (id, name, newick) VALUES (?, ?, ?)")) {
					for (int i = 0; i < networks.size(); i++) {
						var network = networks.get(i);
						ps.setInt(1, i);
						ps.setString(2, network.getName());
						ps.setString(3, newickIO.toBracketString(network, true) + ";");
						ps.addBatch();
					}
					ps.executeBatch();
				}

				try (var ps = conn.prepareStatement("INSERT INTO taxa (id, name, display_label) VALUES (?, ?, ?)")) {
					for (var t = 1; t <= taxaBlock.getNtax(); t++) {
						var taxon = taxaBlock.get(t);
						ps.setInt(1, t);
						ps.setString(2, taxon.getName());
						ps.setString(3, taxon.getDisplayLabelOrName());
						ps.addBatch();
					}
					ps.executeBatch();
				}

				OptionsSQL.save(conn, OptionsSQL.DEFAULT_TABLE, options);

				conn.commit();
			} catch (Exception e) {
				conn.rollback();
				throw e;
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (Exception e) {
			throw new RuntimeException("Error saving database: " + fileName, e);
		}
	}

	public static void load(String fileName, Document document, OptionsRegistry options) throws IOException {
		FileUtils.checkFileReadableNonEmpty(fileName);
		if (!SQLiteUtils.isSQLiteWithTreesOrNetworksTable(fileName))
			throw new IOException("Not a PhyloParallelograms file");

		document.clear();

		var url = "jdbc:sqlite:" + fileName;

		var treeRecords = new ArrayList<TreeRecord>();
		var networks = new TreeMap<Integer, PhyloTree>();
		var taxaBlock = new TaxaBlock();

		try (var conn = DriverManager.getConnection(url);
			 var stmt = conn.createStatement()) {

			stmt.execute("PRAGMA foreign_keys = ON;");

			// networks
			if (tableExists(conn, "networks")) {
				var newickIO = new NewickIO();
				newickIO.setNewickNodeCommentConsumer(CommentData.createDataNodeConsumer());
				newickIO.setNewickEdgeCommentConsumer(CommentData.createDataEdgeConsumer());
				try (var rs = stmt.executeQuery("SELECT id, name, newick FROM networks ORDER BY id")) {
					while (rs.next()) {
						var id = rs.getInt("id");
						var name = rs.getString("name");
						var newick = rs.getString("newick");
						var network = new PhyloTree();
						newickIO.parseBracketNotation(network, newick, true);
						if (name != null && !name.isBlank())
							network.setName(name);
						if (network.getName() == null || network.getName().isBlank())
							network.setName("network-" + id);
						networks.put(id, network);
					}
				}
			}

			// trees
			if (tableExists(conn, "trees")) {
				try (var rs = stmt.executeQuery("SELECT id, name, run, show, color, newick FROM trees ORDER BY id")) {
					while (rs.next()) {
						var id = rs.getInt("id");
						var name = rs.getString("name");
						var newick = rs.getString("newick");
						var colorString = rs.getString("color");

						PhyloTree tree;
						if (newick != null && !newick.isBlank()) {
							tree = new PhyloTree();
							tree.parseBracketNotation(newick, true);
							if (name != null && !name.isBlank())
								tree.setName(name);
							if (tree.getName() == null || tree.getName().isBlank())
								tree.setName("tree-" + id);
						} else tree = null;

						var record = new TreeRecord(name, id, rs.getInt("run") != 0, rs.getInt("show") != 0, tree);
						if (ColorUtilsFX.isColor(colorString)) {
							var color = ColorUtilsFX.parseColor(colorString);
							record.setColor(color);
						}
						treeRecords.add(record);
					}
				}
			}
			// trees
			if (tableExists(conn, "taxa")) {
				try (var rs = stmt.executeQuery("SELECT id, name, display_label FROM taxa ORDER BY id")) {
					while (rs.next()) {
						var id = rs.getInt("id");
						var name = rs.getString("name");
						var displayLabel = rs.getString("display_label");
						taxaBlock.addTaxonByName(name);
						var taxon = taxaBlock.get(taxaBlock.size());
						taxon.setDisplayLabel(displayLabel);
					}
				}
			}

			if (options != null && tableExists(conn, OptionsSQL.DEFAULT_TABLE))
				OptionsSQL.load(conn, OptionsSQL.DEFAULT_TABLE, options);

			if (!treeRecords.isEmpty())
				document.addTreesAndNetworks(treeRecords, networks.values());
			else if (!networks.isEmpty())
				document.addNetworks(networks.values());
			if (taxaBlock.size() > 0) {
				document.getTaxaBlock().clear();
				taxaBlock.getTaxa().forEach(t -> document.getTaxaBlock().add(t));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean tableExists(Connection conn, String tableName) throws SQLException {
		String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";

		try (var ps = conn.prepareStatement(sql)) {
			ps.setString(1, tableName);
			try (var rs = ps.executeQuery()) {
				return rs.next(); // true if at least one row
			}
		}
	}
}
