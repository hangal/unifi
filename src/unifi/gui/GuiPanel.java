package unifi.gui;

import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.apache.bcel.generic.Type;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import unifi.BCP;
import unifi.GoldenUnifiEvent;
import unifi.MethodUnits;
import unifi.UnificationEvent;
import unifi.UnitCollection;

import unifi.gui.PairForUniqueUnitsTable;
import unifi.gui.gui.UnificationEventSelectionListener;
import unifi.gui.gui.UniqueUnitSelectionListener;
import unifi.gui.gui.UnitSelectionListener;
import unifi.jedit.JEditTextArea;
import unifi.jedit.JavaTokenMarker;
import unifi.oo.MethodResolver;
import unifi.units.ConstantUnit;
import unifi.units.FieldUnit;
import unifi.units.MethodParamUnit;
import unifi.units.PhiUnit;
import unifi.units.ReturnValueUnit;
import unifi.units.Unit;
import unifi.util.Util;

class Pair {
	public String gmlfile;
	public SimpleGraph<UnitNode, UnifiEdge> graph;

	public Pair(String gmlFilename, SimpleGraph<UnitNode, UnifiEdge> jgraph) {
		this.gmlfile = gmlFilename;
		this.graph = jgraph;
	}
}
public class GuiPanel extends JPanel {
	private String[] _source_paths;

	boolean showOnlyGoldenUnitViolation=false;
	Collection _all_events, _selected_events, _selected_units,
			_selected_unique_units;
	public Vector unique_units_row_vector = new Vector();

	String _col_names[];
	double _col_pct_widths[];

	public static final String title = "UniFi Units";

	String _currently_displayed_filename; /* this is /path/to/a/b/classname.java */
	String _current_file_contents; /*
									 * this contains contents of
									 * /path/to/a/b/c/classname.java
									 */
	String _data_filename; /*
							 * this is the file from which invariants/violations
							 * are being read
							 */

	JOptionPane _dialog = new JOptionPane();
	JEditTextArea _ta; /* displays source */
	public JTable _events_table, _units_table, unique_units_table;

	AbstractTableModel _events_tm, _units_tm;

	public HashMap<Integer, UnificationEvent> eidToUnifiEvent = new HashMap<Integer, UnificationEvent>();
	public HashMap<Integer, Unit> uidToUnit = new HashMap<Integer, Unit>();

	public HashMap<Unit, Pair> unitToGMLandGraph = new HashMap<Unit, Pair>();
	public GuessCallback guessCallback=null;
	public Unit currSelectedUnit = null, prevSelectedUnit = null;
	public SimpleGraph<UnitNode, UnifiEdge> currentJGraph = null;
	public HashMap<Unit, UnitNode> currentUnitToNode;

	class UniqueUnitSelectionListener implements ListSelectionListener {

		public void valueChanged(ListSelectionEvent e) {
			// Ignore extra messages.
			if (e.getValueIsAdjusting())
				return;

			ListSelectionModel lsm = (ListSelectionModel) e.getSource();

			// if no row is selected, we need to bail out
			if (lsm.isSelectionEmpty())
				return;

			int selectedRow = lsm.getMinSelectionIndex();

			PairForUniqueUnitsTable p = (PairForUniqueUnitsTable) ((TableSorter) unique_units_table
					.getModel()).getValueAt(selectedRow, 1);
			Unit u = p.unit;

			Pair gmlAndGraph = unitToGMLandGraph.get(u);
			String gmlFilename = null;
			if (gmlAndGraph == null) {
				gmlFilename = "/tmp/" + u.toString();
				if (gmlFilename.length()>80) {
					gmlFilename = gmlFilename.substring(0, 80);
				}
				gmlFilename += ".xml";

				System.out.println("Before generating JGraph");
				currentJGraph = generateGraph(Unit._current_unit_collection, u);
				System.out.println("Generating GML");
				generateGML(gmlFilename, currentJGraph);
				System.out.println("Finished generating GML");
				gmlAndGraph = new Pair(gmlFilename, currentJGraph);
				unitToGMLandGraph.put(u, gmlAndGraph);
			} else {
				gmlFilename = gmlAndGraph.gmlfile;
				currentJGraph = gmlAndGraph.graph;
			}

			System.out.println("Calling graphChanged");
			guessCallback.graphChanged(gmlFilename, u.toString(), Unit._current_unit_collection, u);

		}
	}

	private void generateGML(String filename, SimpleGraph<UnitNode, UnifiEdge> g) {
		currentUnitToNode = new HashMap<Unit, UnitNode>(g.vertexSet().size());
		PrintWriter out=null;
		try {
			out = new PrintWriter (new FileOutputStream(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

    	out.println (
    			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
    			"<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n" +
    			"  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
    			"  xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n" +
    			"  http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n" +

    			// attr definition for nodes
    			"  <key id=\"classname\" for=\"node\" attr.name=\"classname\" attr.type=\"string\"/>\n" +
    			"  <key id=\"labelcolor\" for=\"node\" attr.name=\"color\" attr.type=\"string\"/>\n" +
    			"  <key id=\"type\" for=\"node\" attr.name=\"type\" attr.type=\"string\"/>\n" +
    			"  <key id=\"uid\" for=\"node\" attr.name=\"uid\" attr.type=\"string\"/>\n" +

    			// attr definition for edges
    			"  <key id=\"classname\" for=\"edge\" attr.name=\"classname\" attr.type=\"string\"/>\n" +
    			"  <key id=\"type\" for=\"edge\" attr.name=\"type\" attr.type=\"string\"/>\n" +
    			"  <key id=\"eid\" for=\"edge\" attr.name=\"eid\" attr.type=\"string\"/>\n" +
    			"  <key id=\"width\" for=\"edge\" attr.name=\"width\" attr.type=\"int\"/>\n" +
    			"  <key id=\"labelcolor\" for=\"edge\" attr.name=\"color\" attr.type=\"string\"/>\n");

    	out.println ("<graph edgedefault=\"undirected\">\n");
    	String colorsForGoldenUnits[] = {"purple", "violet", "cyan", "brown", "turquoise", "darkblue", "blue" };
    	HashMap<Integer, Integer> goldenIdToColorIdx = new HashMap<Integer, Integer>();
    	int goldenIdIdx=0;
    	for (UnitNode v:g.vertexSet()) {
            uidToUnit.put(v.u.getId(), v.u);
    		currentUnitToNode.put(v.u, v);
    		out.print ("<node id=\"" + escape(v.u)+ "\">\n");
            out.print ("<data key=\"classname\">" + sanitizeForXml(v.u)  + "</data>\n");
            out.print ("<data key=\"uid\">" + v.u.getId()  + "</data>\n");
            Util.ASSERT(v.u.getId()!=0, "unit:"+v.u+" has 0 as its id");
            out.print ("<data key=\"type\">nonexec</data>\n");
            if (v.u.isGolden()) {
            	int gid=v.u.getGoldenId();
            	Integer colorIdx = goldenIdToColorIdx.get(gid);
            	if (colorIdx==null) {
            		colorIdx = goldenIdIdx;
            		goldenIdToColorIdx.put(gid, goldenIdIdx);
            		goldenIdIdx++;
            		goldenIdIdx = goldenIdIdx % colorsForGoldenUnits.length;
            	}
            	out.print ("<data key=\"labelcolor\">" + colorsForGoldenUnits[colorIdx] + "</data>\n");
            } else if (v.u.isField()) {
            	out.print ("<data key=\"labelcolor\">" + "red" + "</data>\n");
            } else if (v.u instanceof MethodParamUnit || v.u instanceof ReturnValueUnit) {
            	out.print ("<data key=\"labelcolor\">" + "orange" + "</data>\n");
    	    } else if (v.u instanceof ConstantUnit) {
            	out.print ("<data key=\"labelcolor\">" + "white" + "</data>\n");
            }
            else {
            	out.print ("<data key=\"labelcolor\">" + "green" + "</data>\n");
            }
            out.print ("</node>\n");
    	}
    	int edgeId=0;
    	for (UnifiEdge e:g.edgeSet()) {
    		UnitNode s = g.getEdgeSource(e);
    		UnitNode t = g.getEdgeTarget(e);

    		e.guessEdgeId = edgeId;
        	out.print ("<edge id=\"" + edgeId + "\" source=\"" + escape(s.u)
        			+ "\" target=\"" + escape(t.u) + "\">\n");
            out.print ("<data key=\"classname\">"+sanitizeForXml(e.e) + "</data>\n");
            Util.ASSERT(e.e.getId()!=0, "event:"+e.e+" has 0 as its id");
            if (e instanceof SuperEdge) {
            	SuperEdge se = (SuperEdge)e;
            	out.print ("<data key=\"eid\">");
            	for (UnificationEvent ue:se.events) {
            		out.print (ue.getId() + ";");
            	}
            	out.print("</data>\n");
            	int minwidth=3, maxwidth=20;
            	int width = minwidth + 3*se.events.size();
            	if (width > maxwidth) width = maxwidth;
            	out.print ("<data key=\"width\">" + width + "</data>\n");
            } else {
            	out.print ("<data key=\"eid\">" + e.e.getId() + "</data>\n");
            	out.print ("<data key=\"width\">" + 3 + "</data>\n");
            }
            out.print ("<data key=\"type\">nonexec</data>\n");
            if (e.isGolden()) {
            	out.print ("<data key=\"labelcolor\">brown</data>\n");
            } else {
            	out.print ("<data key=\"labelcolor\">gray</data>\n");
            }

            out.print ("</edge>\n");
            edgeId++;
    	}
    	out.println("</graph>\n</graphml>\n");
    	out.close();
	}

	private static String sanitizeForXml(Object u) {
	    String s = u.toString();
	    s = "<![CDATA[" + s.replaceAll("]]>", "]]>]]><![CDATA[") + "]]>";
	    s = s.replace((char)0xc, ' ');
	    return s;
	}

	private static String escape(Unit u) {
	    String s = u.toString();
	    s = s.replace("<", ".LT.");
	    s = s.replace(">", ".GT.");
	    s = s.replace("\"", ".BSLASH.");
	    s = s.replace("'", ".QUOTE.");
	    s = s.replace("/", ".SLASH.");
	    s = s.replace("*", ".STAR.");
	    s = s.replace("&", ".AND.");
	    s = s.replace((char)0xc, '_');
	    return s;
	}

	private static SimpleGraph generateGraph(UnitCollection uc, Unit srcUnit) {
		//uc.checkIds();
		SimpleGraph<UnitNode, UnifiEdge> g = new SimpleGraph<UnitNode, UnifiEdge>(
				UnifiEdge.class);


		Collection<Unit> explore = new ArrayList<Unit>(), next_explore = new ArrayList<Unit>();
		next_explore.add(srcUnit);
		Map<Unit, UnitNode> unitToNode = new HashMap<Unit, UnitNode>();
		List<Unit> remainingUnits = new ArrayList<Unit>(uc.get_reps().get(srcUnit));

		UnitNode srcNode = new UnitNode(srcUnit);
		unitToNode.put(srcUnit, srcNode);
		g.addVertex(srcNode);
		while (true) {
			while (next_explore.size() != 0) {
				explore.clear();
				explore.addAll(next_explore);
				next_explore.clear();

				for (Unit u1: explore) {
					UnitNode n1 = unitToNode.get(u1);
					if (n1 == null) {
						n1 = new UnitNode(u1);
						unitToNode.put(u1, n1);
					}
					g.addVertex(n1);

					Collection<UnificationEvent> events = u1.getUnificationEvents();

					for (UnificationEvent e : events) {
						Util.ASSERT(e.getId() != 0);
						List<Unit> relatedUnits = null;
						if (e instanceof GoldenUnifiEvent) {
							relatedUnits = ((GoldenUnifiEvent)e).get_units();
						} else {
							relatedUnits = new ArrayList<Unit>();
							relatedUnits.add(e.get_unit_a());
							relatedUnits.add(e.get_unit_b());
						}
						remainingUnits.removeAll(relatedUnits);

						for (int i=0; i<relatedUnits.size(); i++) {
							Unit u2=relatedUnits.get(i);

							if (u1.equals(u2)) continue;

							UnitNode n2 = unitToNode.get(u2);
							if (n2 == null) {
								n2 = new UnitNode(u2);
								unitToNode.put(u2, n2);
							}

							boolean added = false;
							added = g.addVertex(n2);
							if (added)
								next_explore.add(u2);

							g.addEdge(n1, n2, new UnifiEdge(e));
						}

					}
				}
			}
			break;
			/* XXX: temporarily disabling this...
			if (remainingUnits.size() == 0) {
				break;
			} else {
				for (Unit x:remainingUnits) {
					UnitNode n = unitToNode.get(x);
					if (n==null) {
						n = new UnitNode(x);
						unitToNode.put(x, n);
					}
					g.addVertex(n);
				}
				remainingUnits.clear();
				next_explore = remainingUnits;
			}*/
		}		
		
		//dropMergeNodes(g);
		/*if (g.vertexSet().size() > 4000) {
			System.out.println("Original graph size:" + g.vertexSet().size());
			makeSuperEdge(g);
			System.out.println("After generating super-edge, graph size:"+g.vertexSet().size());

			if (g.vertexSet().size() > 3000) {
				dropBoringNodes(g);
				System.out.println("After dropping boring nodes, graph size:"+g.vertexSet().size());
			}
		}*/

		if (uc.get_reps().get(srcUnit).size() != g.vertexSet().size()) {
			System.out.println("Vertex set size["+g.vertexSet().size()+"] and Unit cluster size["+
					uc.get_reps().get(srcUnit).size() +"] are different");
		}
		return g;
	}
	
	public static void dropMergeNodes(SimpleGraph<UnitNode, UnifiEdge> g) {
		ArrayList<UnitNode> nodesToRemove = new ArrayList<UnitNode>();
		Iterator<UnitNode> it = g.vertexSet().iterator();
		while (it.hasNext()) {
			UnitNode un = it.next();
			
			if (un.getUnit() instanceof PhiUnit) {
				Set<UnifiEdge> edges = g.edgesOf(un);
				if (edges.size() == 2) {
					Iterator<UnifiEdge> eit = edges.iterator();
					UnifiEdge e1 = eit.next(), e2 = eit.next();
					UnitNode n1 = getTheOtherEnd(g, e1, un);
					UnitNode n2 = getTheOtherEnd(g, e1, un);
					nodesToRemove.add(un);
				}
			}
		}
		
		for (UnitNode n:nodesToRemove) {
			Set<UnifiEdge> edges = g.edgesOf(n);
			if (edges.size() == 1) continue;
			Util.ASSERT(edges.size()==2, "Edge size expecting 2, but instead:"+edges.size() + " - " + edges);
			Iterator<UnifiEdge> eit = edges.iterator();
			UnifiEdge e1 = eit.next();
			UnifiEdge e2 = eit.next();
			UnitNode n1 = getTheOtherEnd(g, e1, n);
			UnitNode n2 = getTheOtherEnd(g, e2, n);
			g.removeEdge(e1);
			g.removeEdge(e2);
			g.removeVertex(n);

			// we randomly choose unifi-event of 1st edge 
			// (could've picked 2nd edge's event)
			UnifiEdge ne = new UnifiEdge(e1.e);
			g.addEdge(n1, n2, ne);
		}
	}
	
	public static void makeSuperEdge(SimpleGraph<UnitNode, UnifiEdge> g) {
		ArrayList<UnitNode> nodesToRemove = new ArrayList<UnitNode>();
		for(UnitNode n:g.vertexSet()) {
			/* XXX: even field units are merged into super-edges
			if (n.u instanceof FieldUnit) // || n.u instanceof MethodParamUnit || n.u instanceof ReturnValueUnit)
				continue;
			*/

			Set<UnifiEdge> edges = g.edgesOf(n);
			if (edges.size() == 2) {
				Iterator<UnifiEdge> it = edges.iterator();
				BCP bcp1 = it.next().e.get_bcp();
				BCP bcp2 = it.next().e.get_bcp();

				if (bcp1.get_class_name().equals(bcp2.get_class_name())) {
					nodesToRemove.add(n);
				} else {
					System.out.println("!!!!!");
					System.out.println("WARNING: merging nodes that are in different packages!!!");
					nodesToRemove.add(n);
				}
			}
		}

		for(UnitNode n:nodesToRemove) {
			Set<UnifiEdge> edges = g.edgesOf(n);
			if (edges.size() == 1) continue;
			Util.ASSERT(edges.size()==2, "Edge size expecting 2, but instead:"+edges.size() + " - " + edges);
			Iterator<UnifiEdge> it = edges.iterator();
			UnifiEdge e1 = it.next();
			UnifiEdge e2 = it.next();
			UnitNode n1 = getTheOtherEnd(g, e1, n);
			UnitNode n2 = getTheOtherEnd(g, e2, n);
			g.removeEdge(e1);
			g.removeEdge(e2);
			g.removeVertex(n);

			SuperEdge se = new SuperEdge(e1, e2, n);
			g.addEdge(n1, n2, se);
		}
	}


	public static void dropBoringNodes(SimpleGraph<UnitNode, UnifiEdge> g) {
		ArrayList<UnitNode> nodesToRemove = new ArrayList<UnitNode>();
		ArrayList<UnitNode> boringNodes = new ArrayList<UnitNode>();

		Iterator<UnitNode> iter = g.vertexSet().iterator();
		while(iter.hasNext()) {
			UnitNode n = iter.next();

			Set<UnifiEdge> edges = g.edgesOf(n);
			if (edges.size() <= 1) {
				nodesToRemove.add(n);
			} else if (edges.size() == 2) {
				if (nodesToRemove.contains(n)) continue;

				Iterator<UnifiEdge> it = edges.iterator();
				UnifiEdge e1 = it.next();
				UnifiEdge e2 = it.next();

				boringNodes.clear();
				if (isBoringBranch(g, n, e1, boringNodes, nodesToRemove))
					nodesToRemove.addAll(boringNodes);
				else {
					boringNodes.clear();
					if (isBoringBranch(g, n, e2, boringNodes, nodesToRemove))
						nodesToRemove.addAll(boringNodes);
				}
			}
		}

		System.out.println("removing "+nodesToRemove.size() + " boring nodes");
		for(UnitNode n:nodesToRemove) {
			g.removeVertex(n);
		}
	}
	/*
	public static void dropBoringNodes(SimpleGraph<UnitNode, UnifiEdge> g) {
		ArrayList<UnitNode> nodesToRemove = new ArrayList<UnitNode>();
		ArrayList<UnitNode> boringNodes = new ArrayList<UnitNode>();
		for(UnitNode n:g.vertexSet()) {
			if (nodesToRemove.contains(n))
				continue;
			Set<UnifiEdge> edges = g.edgesOf(n);
			if (edges.size() == 2) {
				Iterator<UnifiEdge> it = edges.iterator();
				UnifiEdge e1 = it.next();
				UnifiEdge e2 = it.next();

				boringNodes.clear();
				if (isBoringBranch(g, n, e1, boringNodes, nodesToRemove))
					nodesToRemove.addAll(boringNodes);
				else {
					boringNodes.clear();
					if (isBoringBranch(g, n, e2, boringNodes, nodesToRemove))
						nodesToRemove.addAll(boringNodes);
				}
			}
		}

		System.out.println("removing "+nodesToRemove.size() + " boring nodes");
		for(UnitNode n:nodesToRemove) {
			g.removeVertex(n);
		}
	}*/

	public static boolean isBoringBranch(SimpleGraph<UnitNode, UnifiEdge> g, UnitNode node, UnifiEdge edge, List<UnitNode> boringNodes, List<UnitNode> removedNodes) {
		UnitNode n1 = getTheOtherEnd(g, edge, node);
		boringNodes.add(node);

		Collection<UnitNode> explore = new ArrayList<UnitNode>(), next_explore = new ArrayList<UnitNode>();
		next_explore.add(n1);
		final int MaxIteration = 9;
		int iteration = 0;
		while(next_explore.size()>0 && iteration < MaxIteration) {
			boringNodes.addAll(next_explore);
			explore.clear();
			explore.addAll(next_explore);
			next_explore.clear();
			for(UnitNode n:explore) {
				for(UnifiEdge e:g.edgesOf(n)) {
					UnitNode n2 = getTheOtherEnd(g, e, n);
					if (n2.u instanceof FieldUnit) {
						return false;
					}
					if (! boringNodes.contains(n2) && !next_explore.contains(n2))
						next_explore.add(n2);
				}
			}
			iteration++;
		}

		if (next_explore.size() == 0) return true;

		System.out.println("isBoringBranch(): returning false, next_explore.size():"+next_explore.size());
		return false;
	}

	public static UnitNode getTheOtherEnd(SimpleGraph<UnitNode, UnifiEdge> g, UnifiEdge e, UnitNode n) {
		UnitNode s = g.getEdgeSource(e);
		UnitNode t = g.getEdgeTarget(e);
		if (s.equals(n)) return t;
		else return s;
	}

	public GuiPanel(String[] sourcepaths, String uc_filename) {
		String diffGolden = System.getProperty("unifi.diff.golden");
		if (! Util.nullOrEmpty(diffGolden)) {
			System.out.println("\n\nShowing only golden unit violations.");
			showOnlyGoldenUnitViolation=true;
		}

		_source_paths = sourcepaths;
		_data_filename = uc_filename;

		parse_outfile(_data_filename);
		//display_all();
	}

        public void filterAndSortClusters(Collection<Unit> reps) {
                // filtering
		Iterator<Vector> it = unique_units_row_vector.iterator();
                Vector[] rows = new Vector[reps.size()];
                ArrayList<Unit> arrList = new ArrayList<Unit>();
                arrList.addAll(reps);

		while(it.hasNext()) {
			Vector row_data =  it.next();
			PairForUniqueUnitsTable p = (PairForUniqueUnitsTable)row_data.get(1);
                        if (reps.contains(p.unit)) {
                            int idx = arrList.indexOf(p.unit);
                            rows[idx] = row_data;
                        } else {
                            it.remove();
                        }
		}

                unique_units_row_vector.clear();
                unique_units_row_vector.setSize(0);
                for (int i=0; i<rows.length; i++) {
                        unique_units_row_vector.add(rows[i]);
                }
        }

	public void display_all() {
		this.setLayout(new GridLayout(1, 1));

		_ta = new JEditTextArea();
		_ta.setTokenMarker(new JavaTokenMarker());

		String unique_unit_col_names[] = { "#", "Unit", "#elements", "Type" };
		double unique_unit_col_pct_widths[] = { 0.04, 0.76, 0.1, 0.1 }; // must add up to 1

		unique_units_table = new MyJTable(unique_units_row_vector,
				new UniqueUnitSelectionListener(), unique_unit_col_names,
				unique_unit_col_pct_widths);

		JScrollPane scroll = new JScrollPane(unique_units_table);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, _ta, scroll);
		splitPane.setOneTouchExpandable(true);

		JButton showPath = new JButton("Show path");
		JCheckBox togglePathOnly = new JCheckBox("Show path ONLY");

		showPath.addActionListener(new ActionListener() {
			/*@Override
			public void actionPerformed(ActionEvent e) {
				if (currSelectedUnit != null && prevSelectedUnit != null) {
					UnitNode currUnitNode = currentUnitToNode.get(currSelectedUnit);
					UnitNode prevUnitNode = currentUnitToNode.get(prevSelectedUnit);
					Util.ASSERT(currUnitNode!=null);
					Util.ASSERT(prevUnitNode!=null);

					List<UnifiEdge> edges = DijkstraShortestPath.findPathBetween(currentJGraph, currUnitNode, prevUnitNode);
					guessCallback.pathFound(edges);
				}
			}*/
			@Override
			public void actionPerformed(ActionEvent e) {
				guessCallback.findPathBetweenHulls();
			}
		});

		togglePathOnly.setSelected(false);
		togglePathOnly.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.DESELECTED) {
					guessCallback.showPathOnly(0);
				} else {
					guessCallback.showPathOnly(1);
				}
			}
		});

		JSplitPane buttons = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, showPath, togglePathOnly);
		JSplitPane biggerSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buttons, splitPane);

		this.add(biggerSplitPane);
		this.setVisible(true);
	}

	public void parse_outfile(String filename) {
		UnitCollection uc = null;

		System.out.println("Opening file:"+filename);
		// read from a file
		ObjectInputStream ois;
		try {
			ois = new ObjectInputStream(new FileInputStream(filename));
		} catch (Exception e) {
			System.err.println("Warning: error opening file: " + filename);
			return;
		}

		System.out.println("reading UnitCollection object");
		try {
			uc = (UnitCollection) ois.readObject();

			MethodResolver.globalMethodUnitsDir = (Map<String, MethodUnits>) ois.readObject();
			FieldUnit.globalFieldUnitDir = (Map<String, FieldUnit>) ois.readObject();

			_all_events = _selected_events = uc.get_events();
			ois.close();
		} catch (Exception e) {
			System.err.println("Warning: error reading data from file "
					+ filename);
			System.err.println(e);
			Util.ASSERT(false);
		}

		System.out.println("reading UnitCollection done!");

		// need the foll. because other code refers to this static later
		Unit._current_unit_collection = uc;
		uc.compute_reps();
		//_selected_units = uc.get_units();
		_selected_unique_units = uc.get_all_unique_units_sorted_by_class_size();
		// uc.print_units();

		//initialize_events_row_vector(_selected_events);
		//initialize_units_row_vector(_selected_units);
		initialize_unique_units_row_vector(_selected_unique_units, uc);

		createEidToUnifiEventMap(uc);
	}

    public Unit getUnit(int uid) {
    	return uidToUnit.get(uid);
    }

	void createEidToUnifiEventMap(UnitCollection uc) {
		Collection<UnificationEvent> all_events = uc.get_events();

		for (UnificationEvent ue:all_events) {
			eidToUnifiEvent.put(ue.getId(), ue);
		}
	}

	public UnificationEvent getUnifiEvent(int eid) {
		return eidToUnifiEvent.get(eid);
	}

	public void display_source(int eid) {
		UnificationEvent e = eidToUnifiEvent.get(eid);
                Util.ASSERT(e!=null);
		BCP bcp = e.get_bcp();
		String s = bcp.get_class_name();
		display_src(s, bcp.get_src_line() - 1);
	}

	public void display_source2(int[] eidlist) {
		List<UnificationEvent> ues = new ArrayList<UnificationEvent>();
		for (int eid:eidlist) {
			ues.add(eidToUnifiEvent.get(eid));
		}
		String s = ues.get(0).get_bcp().get_class_name();
		List<Integer> srclines = new ArrayList<Integer>();
		for (UnificationEvent e:ues) {
			Util.ASSERT(e!=null);
			BCP bcp = e.get_bcp();
			srclines.add(bcp.get_src_line()-1);
		}
		display_src2(s, srclines);
	}

        public void display_test(String teststr) {
                _ta.setText(teststr);
        }

	void initialize_unique_units_row_vector(Collection<Unit> unique_units,
			UnitCollection uc) {
		unique_units_row_vector.clear();
		Collection<Unit> selected_units = null;
		if (showOnlyGoldenUnitViolation) {
			System.out.println("Filtering out golden unit violations");
			selected_units = new ArrayList<Unit>();
			outer:
			for (Unit rep : unique_units) {
				List<Unit> unitCluster = Unit._current_unit_collection.get_reps().get(rep);
				int prevGoldenId=-1;
				if (unitCluster.size() <= 2) continue;
				for (Unit u:unitCluster) {
					if (u.isGolden()) {
						if (prevGoldenId != -1 && prevGoldenId != u.getGoldenId()){
							selected_units.add(rep);
							prevGoldenId=-1;
							continue outer;
						}
						prevGoldenId = u.getGoldenId();
					}
				}
			}
			System.out.println("Finished golden unit violation filtration.");
		} else {
			selected_units = unique_units;
		}

		int num = 0;
		for (Unit rep : selected_units) {
			num++;

			String displayName = uc.getAllDisplayNames(rep);

			Vector<Object> row_data = new Vector<Object>(); // row values
			row_data.addElement(new Integer(num));
			PairForUniqueUnitsTable p = new PairForUniqueUnitsTable();
			p.unit = rep;
			p.displayName = displayName;
			row_data.addElement(p);
			row_data.addElement(new Integer(uc.get_num_units_for_rep(rep)));
			// row_data.addElement ("Class"); //TOFIX
			// row_data.addElement ("Method"); //TOFIX
			Type t = rep.getType();
			row_data.addElement((t != null) ? t.toString() : "");
			unique_units_row_vector.addElement(row_data);
		}
	}

	/* classname is still with dots, not slashes */
	public void display_src(String classname, int lineno) {
		String s = classname.replace('.', File.separatorChar);
		int x = s.indexOf('$'); // if inner class drop it
		if (x >= 0)
			s = s.substring(0, x);
		s += ".java";

		readFile(s);
		// setTitle (_currently_displayed_filename);
		_ta.setText(_current_file_contents);

		if (lineno > _ta.getLineCount() || lineno < 0)
			lineno = 0;
		_ta.setCaretPosition(_ta.getLineStartOffset(lineno));
		_ta.updateScrollBars();
	}

	public void display_src2(String classname, List<Integer> lineNos) {
		String s = classname.replace('.', File.separatorChar);
		int x = s.indexOf('$'); // if inner class drop it
		if (x >= 0)
			s = s.substring(0, x);
		s += ".java";

		readFile(s);
		// setTitle (_currently_displayed_filename);
		_ta.setText(_current_file_contents);

		for(int lineno:lineNos) {
			if (lineno > _ta.getLineCount() || lineno < 0)
				lineno = 0;
			_ta.setCaretPosition(_ta.getLineStartOffset(lineno));
		}
		_ta.updateScrollBars();
	}

	public void readFile(String filename) {
		String full_path = null;
		boolean found = false;

		// handle a common case
		String suffix = File.separatorChar + filename;
		if (_currently_displayed_filename != null)
			if (_currently_displayed_filename.endsWith(suffix))
				return;

		for (int i = 0; i < _source_paths.length; i++) {
			full_path = _source_paths[i] + File.separatorChar + filename;
			File f = new File(full_path);

			if (f.exists()) {
				found = true;
				break;
			}
		}

		if (!found) {
			String s = "Sorry. unable to find file \"" + filename + '"'
					+ " on source path, which is currently set to:\n";
			for (int i = 0; i < _source_paths.length; i++)
				s += '"' + _source_paths[i] + '"' + "\n"
						+ "Define the property unifi.sp to view sources";
			_current_file_contents = s;
			_currently_displayed_filename = "File not found";
			return;
		}

		_currently_displayed_filename = full_path;

		StringBuffer sb = new StringBuffer();

		try {

			LineNumberReader r = new LineNumberReader(new InputStreamReader(
					new FileInputStream(full_path)));

			while (true) {
				String s = r.readLine();
				if (s == null)
					break;

				sb.append(r.getLineNumber() + ": ");
				sb.append(s);
				sb.append("\n");
			}
			_current_file_contents = sb.toString();
			r.close();
			return;
		} catch (IOException e) {
			_current_file_contents = "Sorry: Exception trying to read file: "
					+ filename + "\n" + e;
			_currently_displayed_filename = "File not found";
			return;
		}
	}

	public void updateNodeSelection(Unit u) {
		prevSelectedUnit = currSelectedUnit;
		currSelectedUnit = u;
	}
}
