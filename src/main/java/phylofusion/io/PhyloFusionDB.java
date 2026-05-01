/*
 * PhyloFusionDB.java Copyright (C) 2026 Daniel H. Huson
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

package phylofusion.io;

import jloda.fx.util.ColorSchemeManager;
import jloda.phylo.CommentData;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.FileUtils;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import phylofusion.model.Document;
import phylofusion.window.TreeRecord;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class PhyloFusionDB {

	public static void save(String fileName,
							List<TreeRecord> treeRecords,
							List<PhyloTree> networks,
							double minConfidence, double outlineWidth, boolean showOutline, String colorScheme) throws IOException {
		String url = "jdbc:sqlite:" + fileName;

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
								CREATE TABLE IF NOT EXISTS parameters (
									name   TEXT PRIMARY KEY,
									type   TEXT NOT NULL,
									value  TEXT
								);
							""");

					// Clear old contents
					stmt.executeUpdate("DELETE FROM trees");
					stmt.executeUpdate("DELETE FROM networks");
					stmt.executeUpdate("DELETE FROM parameters");
				}

				var newickIO = new NewickIO();
				newickIO.setNewickNodeCommentSupplier(CommentData.createDataNodeSupplier());
				newickIO.setNewickEdgeCommentSupplier(CommentData.createDataEdgeSupplier());


				try (var ps = conn.prepareStatement(
						"INSERT INTO trees (id, name, run, show, newick) VALUES (?, ?, ?, ?, ?)")) {
					for (var record : treeRecords) {
						ps.setInt(1, record.getId());
						ps.setString(2, record.getName());
						ps.setInt(3, record.isRun() ? 1 : 0);
						ps.setInt(4, record.isShow() ? 1 : 0);
						var newick = record.getTree() == null ? "" : newickIO.toBracketString(record.getTree(), true) + ";";
						ps.setString(5, newick);
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

				try (var ps = conn.prepareStatement("INSERT INTO parameters (name, type, value) VALUES (?, ?, ?)")) {
					{
						ps.setString(1, "min_confidence");
						ps.setString(2, "double");
						ps.setString(3, StringUtils.removeTrailingZerosAfterDot(minConfidence));
						ps.addBatch();
					}
					{
						ps.setString(1, "outline_width");
						ps.setString(2, "double");
						ps.setString(3, StringUtils.removeTrailingZerosAfterDot(outlineWidth));
						ps.addBatch();
					}
					{
						ps.setString(1, "show_outline");
						ps.setString(2, "boolean");
						ps.setString(3, showOutline ? "true" : "false");
						ps.addBatch();
					}
					{
						ps.setString(1, "color_scheme");
						ps.setString(2, "string");
						ps.setString(3, colorScheme);
					}
					ps.executeBatch();
				}

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

	public static Parameters load(String fileName, Document document) throws IOException {
		FileUtils.checkFileReadableNonEmpty(fileName);
		if (!SQLiteUtils.isSQLiteWithTreesOrNetworksTable(fileName))
			throw new IOException("Not a PhyloFusion file");

		document.clear();

		var url = "jdbc:sqlite:" + fileName;

		var treeRecords = new ArrayList<TreeRecord>();
		var networks = new TreeMap<Integer, PhyloTree>();

		Parameters result;

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
				try (var rs = stmt.executeQuery("SELECT id, name, run, show, newick FROM trees ORDER BY id")) {
					while (rs.next()) {
						var id = rs.getInt("id");
						var name = rs.getString("name");
						var newick = rs.getString("newick");

						PhyloTree tree;
						if (newick != null && !newick.isBlank()) {
							tree = new PhyloTree();
							tree.parseBracketNotation(newick, true);
							if (name != null && !name.isBlank())
								tree.setName(name);
							if (tree.getName() == null || tree.getName().isBlank())
								tree.setName("tree-" + id);
						} else tree = null;
						treeRecords.add(new TreeRecord(name, id, rs.getInt("run") != 0, rs.getInt("show") != 0, tree));
					}
				}
			}

			try (var rs = stmt.executeQuery("SELECT name, type, value FROM parameters ORDER BY name")) {
				var confidenceThreshold = -1.0;
				var outlineWidth = -1.0;
				var showOutline = true;
				var colorScheme = document.getColorSchemeName();
				while (rs.next()) {
					var name = rs.getString("name");
					var type = rs.getString("type");
					var value = rs.getString("value");
					if (type.equals("double") && NumberUtils.isDouble(value)) {
						if (name.equals("confidence_threshold")) {
							confidenceThreshold = Double.parseDouble(value);
						} else if (name.equals("outline_width")) {
							outlineWidth = Double.parseDouble(value);
						}
					} else if (type.equals("boolean") && NumberUtils.isBoolean(value)) {
						if (name.equals("show_outline")) {
							showOutline = NumberUtils.parseBoolean(value);
						}
					} else if (type.equals("string")) {
						if (name.equals("color_scheme")) {
							if (ColorSchemeManager.getInstance().getNames().contains(value))
								colorScheme = value;
						}
					}
				}
				result = new Parameters(confidenceThreshold, outlineWidth, showOutline, colorScheme);
			}
			if (!treeRecords.isEmpty())
				document.addTreesAndNetworks(treeRecords, networks.values());
			else if (!networks.isEmpty())
				document.addNetworks(networks.values());

		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return result;
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

	public record Parameters(double confidenceThreshold, double outlineWidth, boolean showOutline, String colorScheme) {
	}
}
