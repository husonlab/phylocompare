# PhyloCompare User Manual

**Version 1.0 (draft)**
**Daniel H. Huson, 2026**

> *Keyboard shortcuts:* throughout this manual, `Cmd` denotes the platform shortcut modifier -- Cmd on macOS, Ctrl on
> Windows and Linux.

---

PhyloCompare author: Daniel H. Huson
Contributors: Banu Cetinkaya (tree tracing in PhyloFusion) and Louxin Zhang (PhyloFusion algorithm)

## 1. Introduction

PhyloCompare is a JavaFX desktop application for computing and displaying a **(phylogenetic) Parallelogram**, that is a
new type of tree comparison that draws two or more trees co-located in parallel to show where they have the same
topology and where they differ.

Under the hood, PhyloCompare uses the **PhyloFusion** algorithm to compute an underlying rooted network that contains
all trees and aims at minimizing the number of required reticulations. The parallelogram is then obtained by tracing
each input tree through this network and drawing the resulting embeddings side-by-side, so that shared edges line up and
disagreements stand out as divergent branches.

Typical use cases:

- **Comparing alternative phylogenies** of the same set of taxa -- for example, gene trees from different loci, or trees
  inferred under different models or methods (ML vs. Bayesian, different partitionings) -- and seeing at a glance where
  they agree and where they disagree.
- **Locating sources of incongruence**: regions of the parallelogram where the trees split apart pinpoint clades whose
  placement is unstable, and correspond directly to the reticulations in the underlying network.
- **Communicating phylogenetic results** in talks, papers, and teaching, where a single parallelogram conveys
  topological agreement across multiple trees far more clearly than a row of separately drawn trees.
- **Producing publication-quality figures** of the parallelogram and its underlying network in several diagram styles (
  rectangular, circular, radial; cladogram or phylogram).

**Key features**

- Parallelogram visualization of two or more rooted trees on overlapping taxon sets, with shared structure aligned and
  disagreements made visually explicit.
- Interactive tree table for selecting which trees contribute to the underlying network and which are drawn in the
  parallelogram.
- Color-coded tree overlays with an automatically generated legend.
- Confidence-based filtering of input tree branches before computation.
- Six diagram layouts (rectangular, circular, or radial; cladogram or phylogram).
- Optional rendering of transfer edges and outline-style display of the underlying network.
- Export to image and Newick formats; copy trees or network to the clipboard.

## 2. Installation

Installers are available from [https://github.com/husonlab/phylocompare](https://github.com/husonlab/phylocompare).

## 3. The main window at a glance

The main window is split vertically into two panels:

| Panel                            | Purpose                                                                                                                                          |
|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| **Left -- Tree table**           | Lists all loaded input trees. Two checkbox columns control which trees are used in the PhyloFusion run and which are drawn in the parallelogram. |
| **Right -- Visualization panel** | Displays the computed rooted network together with the phylogenetic parallelogram drawn over it.                                                 |

A status bar at the bottom shows progress messages and a live memory-usage indicator.

### 3.1 Tree table (left panel)

The table has three columns:

- **Tree** -- the name (and optional metadata) of each input tree.
- **Run** -- checkbox: include this tree when running PhyloFusion.
- **Show** -- checkbox: include this tree in the parallelogram drawn on the network.

Above the table:

- **Select all** / **Select none** -- toggle the selection state of all rows in the table.
- **Run** -- run the PhyloFusion algorithm on the trees whose *Run* box is checked.
- **Show** -- refresh the parallelogram to include exactly the trees whose *Show* box is checked.

Below the table:

- **Min branch confidence** -- a numeric field. Input branches with confidence values below this threshold are pruned
  from each input tree before PhyloFusion is run. Set to `0` to disable filtering.

### 3.2 Visualization panel (right panel)

The right panel renders the computed rooted network and, on top of it, the phylogenetic parallelogram formed by the
trees whose *Show* box is checked. Its toolbar contains:

- **Diagram** -- opens a menu for choosing the diagram type (see Section 6.4).
- **Settings** -- opens a menu with rendering options:
    - *Show Outline* -- show the outline the underlying network.
    - *Spread* spinner -- controls the spread of the trees drawn in the parallelogram.
    - *Curved Reticulate Edges* -- draw reticulation edges as curves rather than rectangular segments.
  - *Use Transfer Edges* -- use horizontal-transfer-style edges in addition to standard reticulation edges.
  - *Min Percent* spinner -- minimum percentage of trees that must use an edge for it to qualify as a transfer acceptor
    edge.
- **Color scheme** -- drop-down menu of color schemes used to color the trees in the parallelogram.
- **Zoom In** -- zoom the view in by one step.
- **Zoom Out** -- zoom the view out by one step.

In the top right of the visualization panel:

- **Export** -- menu button with shortcuts to:
    - *Copy Trees* -- copy the currently selected input trees to the clipboard (Newick).
    - *Copy Network* -- copy the underlying network to the clipboard.
    - *Copy Image* -- copy the computed image to the clipboard.

A floating legend (top right) shows the color assigned to each tree in the parallelogram.

## 4. Loading and saving data

### 4.1 Supported input formats

- **Newick** -- one rooted tree per line (or one tree per `;`-terminated block). This is the primary input format.
- **Nexus** -- a file in Nexus format containing a list of trees.
- **Tree names** -- a plain-text list of tree labels, importable via *File > Import > Tree Names...* (used to label
  trees that were loaded from a source that did not provide names).

(Note on advanced usage: The entries in a Newick or Nexus are expected to be trees. However, if the file contains
exactly one item that is a rooted
phylogenetic network that contains `TT` node and edge annotations, then this will be interpreted as a rooted network
that has embedded trees.)

### 4.2 Opening files

- **File > New...** (`Cmd+N`) -- open a new, empty PhyloCompare window.
- **File > Open...** (`Cmd+O`) -- open a file containing one or more rooted trees.
- **File > Recent** -- re-open a recently used file.

### 4.3 Saving and exporting

- **File > Save...** (`Cmd+S`) -- save the current session (input trees, run settings, computed network, parallelogram
  selection) to a PhyloCompare document with file extension `phycmp`.
- **File > Export > Image...** -- save the current visualization (network plus parallelogram) as an image. Supported
  image formats: PNG, SVG and PDF.
- **File > Export > Newick...** -- export the computed network (or the selected trees) in extended Newick format.
- **File > Page Setup...** / **File > Print...** (`Cmd+P`) -- printing.

## 5. A typical workflow

1. **File > Open...** a file containing your rooted input trees.
2. The trees appear in the table on the left. By default, the first are checked in both the *Run* and *Show* columns,
   and are displayed together on the right.
3. (Optional) Set a **Min branch confidence** value to prune low-support branches before running PhyloFusion.
4. (Optional) Use **Select None** and then check only the trees you want to include in the analysis.
5. Click **Run** (or **Run > Run PhyloFusion**, `Cmd+R`).
6. The computed rooted network appears in the right-hand panel, with the parallelogram of all *Show*-checked trees drawn
   on top.
7. Adjust which trees appear in the parallelogram using the *Show* checkboxes in the table, then click **Show** (or *
   *Run > Show Selected Trees**).
8. Tune the visual appearance via the **Diagram** and **Settings** menus.
9. Export the figure via **File > Export > Image...** or copy it to the clipboard from the *Export* menu in the
   visualization panel.

## 6. Menu reference

### 6.1 File menu

| Item                   | Shortcut | Action                                        |
|------------------------|----------|-----------------------------------------------|
| New...                 | `Cmd+N`  | Open an empty window.                         |
| Open...                | `Cmd+O`  | Open a file of rooted input trees.            |
| Recent                 | --       | Recently opened files.                        |
| Import > Tree Names... | --       | Import a list of tree names.                  |
| Export > Image...      | --       | Export the current visualization as an image. |
| Export > Newick...     | --       | Export trees or network in Newick format.     |
| Save...                | `Cmd+S`  | Save the current session.                     |
| Page Setup...          | --       | Configure print page settings.                |
| Print...               | `Cmd+P`  | Print the current view.                       |
| Close                  | `Cmd+W`  | Close the current window.                     |
| Quit                   | `Cmd+Q`  | Exit PhyloCompare.                            |

### 6.2 Edit menu

Standard editing commands: **Undo** (`Cmd+Z`), **Redo** (`Shift+Cmd+Z`), **Cut** (`Cmd+X`), **Copy** (`Cmd+C`), **Copy
Image** (`Shift+Cmd+C`), **Paste** (`Cmd+V`), **Delete** (`Backspace`), **Clear**, **Find...** (`Cmd+F`), **Find Again
** (`Cmd+G`).

### 6.3 Run menu

| Item                        | Shortcut | Action                                                             |
|-----------------------------|----------|--------------------------------------------------------------------|
| Use All Trees               | --       | Check every (selected) tree's *Run* box.                           |
| Use None Trees              | --       | Uncheck every (selected) tree's *Run* box.                         |
| Run PhyloFusion             | `Cmd+R`  | Run PhyloFusion on the selected input trees.                       |
| Show All Trees              | --       | Check every (selected) tree's *Show* box.                          |
| Show None Trees             | --       | Uncheck every (selected)  tree's *Show* box.                       |
| Show Selected Trees         | --       | Redraw the parallelogram using only the trees with *Show* checked. |
| Show Trees Exhaustive       | --       | Use the exhaustive algorithm to locate all trees in the network.   |
| Set Confidence Threshold... | --       | Open a dialog to set the *Mininum branch confidence* value.        |

### 6.4 View menu

- **Enter Full Screen** (`Ctrl+Cmd+F`)
- **Use Dark Theme** -- toggle between light and dark UI themes.
- **Increase / Decrease Font Size** (`Shift+Cmd+Up` / `Shift+Cmd+Down`)
- **Zoom In / Out / To Fit** (`Cmd+Up` / `Cmd+Down` / `Cmd+.`)
- **Diagram Type** -- choose one of:
    - Rectangular Cladogram
    - Rectangular Phylogram
    - Circular Cladogram
    - Circular Phylogram
    - Radial Cladogram
    - Radial Phylogram
- **Show Outline** -- render in outline mode.
- **Show Transfer Edges** -- display transfer-style edges.
- **Curved Reticulate Edges** -- draw reticulation edges as curves.

### 6.5 Window menu

- **Set Window Size...** -- open a dialog to enter exact pixel dimensions for the window. Useful when preparing figures
  for a specific output size.

### 6.6 Help menu

- **Check for Updates...** -- query the update server.
- **About...** -- version and citation information.
- **Help Window...** -- open this manual in an in-app window.

## 7. The PhyloFusion algorithm

Given a collection of rooted phylogenetic trees that may include multifurcations,
the PhyloFusion algorithm computes a rooted phylogenetic network N that displays all input trees and attempts to
minimizes the hybridization number h(N).

See our paper for more details:

L. Zhang, B. Cetinkaya, D. H. Huson, PhyloFusion—Fast and Easy Fusion of Rooted Phylogenetic Trees into Rooted
Phylogenetic Networks, Systematic Biology, 75(1):88-99,
2026 [https://doi.org/10.1093/sysbio/syaf049](https://doi.org/10.1093/sysbio/syaf049)

## 8. The phylogenetic parallelogram

The *phylogenetic parallelogram* is the central visualization in PhyloCompare.
A given set of rooted input trees are drawn in parallel in the same coordinate frame,
guided by an underlying rooted network *N* (computed by PhyloFusion).

The construction proceeds in three steps:

1. **Initial embeddings.** For each input tree *T* of the PhyloFusion algorithm, the algorithm reports which nodes and
   reticulate edges in the network correspond to the tree.
2. **Additional embeddings.** For each additional tree *T* to be displayed, a brute-force algorithm is used to determine
   if and how *T* in contained in *N*.
3. **Co-display.** The embeddings of all *Show*-checked trees are drawn together on top of *N*, each in its own color
   from the active color scheme. Where two or more trees agree on an edge, the embeddings are parallel; where they
   disagree, they fan out and the divergence becomes visually obvious.
   This is what makes the picture a *parallelogram*: shared topology lines up, while disagreement creates parallel
   branches.

The legend in the top right of the visualization panel shows the color assigned to each tree currently included in the
parallelogram.

## 9. Citation

If you use PhyloCompare in published work, please cite:

> Huson, D. H., B. Cetinkaya and L. Zhang ..., *Visualizing agreement and conflict among phylogenetic trees with
PhyloCompare*, manuscript in preparation.

and the underlying PhyloFusion paper:

> L. Zhang, B. Cetinkaya, D. H. Huson, PhyloFusion—Fast and Easy Fusion of Rooted Phylogenetic Trees into Rooted
> Phylogenetic Networks, Systematic Biology, 75(1):88-99,
> 2026 [https://doi.org/10.1093/sysbio/syaf049](https://doi.org/10.1093/sysbio/syaf049)

## 10. License

PhyloCompare is released under the **GNU General Public License, version 3 or later** (GPL-3.0-or-later). See
the `LICENSE` file shipped with the distribution, or <https://www.gnu.org/licenses/>.

---

*Manual last updated: May 2026.*
