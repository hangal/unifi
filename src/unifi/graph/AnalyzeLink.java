package unifi.graph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.Multigraph;
import org.jgrapht.graph.SimpleGraph;

import unifi.UnificationEvent;
import unifi.UnitCollection;
import unifi.units.Unit;
import unifi.util.Util;

class UnifiEdge extends DefaultEdge {
	UnificationEvent e;

	UnifiEdge(UnificationEvent e) {
		this.e = e;
	}

	public String toString() {
		String str = e.toString();
		return str;
	}

	public int size() {
		return 1;
	}
}

class UnitNode {
	Unit u;

	public UnitNode(Unit u) {
		this.u = u;
	}

	public int size() {
		return 1;// +units.size();
	}

	public Unit getUnit() {
		return u;
	}
	
	@Override
	public boolean equals(Object other) {
		if (other instanceof UnitNode) {
			if (u.toString().equals(((UnitNode)other).u.toString())) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return u.toString().hashCode();
	}
}

public class AnalyzeLink {
	static Map<Unit, SimpleGraph<UnitNode, UnifiEdge>> unitToGraph = new HashMap<Unit, SimpleGraph<UnitNode, UnifiEdge>>();
	private static void contract_edge(SimpleGraph<UnitNode, UnifiEdge> g,
									  UnifiEdge chosenEdge) {
		UnifiEdge e1 = (UnifiEdge) chosenEdge;
		UnitNode u1 = g.getEdgeSource(e1);
		UnitNode u2 = g.getEdgeTarget(e1);

		Set<UnifiEdge> edgesOfU1, edgesOfU2;
		edgesOfU1 = g.edgesOf(u1);
		edgesOfU2 = g.edgesOf(u2);

		assert (g.vertexSet().contains(u1));
		assert (g.vertexSet().contains(u2));

		if (edgesOfU1.size() >= edgesOfU2.size()) {
			really_contract_edge(g, u1, u2, edgesOfU2);
		} else {
			really_contract_edge(g, u2, u1, edgesOfU1);
		}
		// System.out.println("EXIT contract_edge: edge size:" +
		// g.edgeSet().size());
	}

	private static SimpleGraph<UnitNode, UnifiEdge> contract_for_min_cut(
			SimpleGraph<UnitNode, UnifiEdge> g, int sizelimit) {
		SimpleGraph<UnitNode, UnifiEdge> g2 = (SimpleGraph<UnitNode, UnifiEdge>) g
				.clone();

		Random r = new Random();
		int size = g2.vertexSet().size();
		Util.ASSERT(size == g.vertexSet().size());
		int prev_size = size;
		while (size > sizelimit) {
			// ConnectivityInspector<UnitNode, UnifiEdge> ci = new
			// ConnectivityInspector<UnitNode, UnifiEdge>(g2);
			// Util.ASSERT(ci.isGraphConnected());

			Object e[] = g2.edgeSet().toArray();
			Util.ASSERT(e.length > 0);
			int idx = r.nextInt(e.length);

			UnifiEdge chosenEdge = (UnifiEdge) e[idx];
			contract_edge(g2, chosenEdge);
			prev_size = size;
			size = g2.vertexSet().size();
			Util.ASSERT(prev_size == size + 1);
			Util.ASSERT(g2.edgeSet().size() > 0);
			Util.ASSERT(g2.vertexSet().size() < g.vertexSet().size());

			// Util.ASSERT(ci.isGraphConnected());
		}
		return g2;
	}

	private static void really_contract_edge(SimpleGraph<UnitNode, UnifiEdge> g,
			UnitNode u1, UnitNode u2, Set<UnifiEdge> edgesOfU2) {

		Set<UnifiEdge> clonedEdgesOfU2 = new LinkedHashSet<UnifiEdge>();
		clonedEdgesOfU2.addAll(edgesOfU2);
		for (UnifiEdge edge : clonedEdgesOfU2) {
			UnitNode a = g.getEdgeSource(edge), b = g.getEdgeTarget(edge);
			UnitNode other = a.equals(u2) ? b : a;

			Util.ASSERT(!other.equals(u2));

			if (!other.equals(u1)) {
				g.addEdge(u1, other, new UnifiEdge(edge.e));
			}
			g.removeEdge(edge);
		}
		g.removeEdge(u1, u2);
		g.removeVertex(u2);
	}

	private static Set<UnifiEdge> brute_force_min_cut(SimpleGraph<UnitNode, UnifiEdge> g) {
		Object[] edges = g.edgeSet().toArray();
		int vertices = g.vertexSet().size();

		/*
		 int all_possible_edges = (vertices - 1) * vertices / 2;
		 if (g.edgeSet().size() > 8 && g.edgeSet().size() >=
		 	 all_possible_edges * 0.9) {
		 	System.out.println("giving up on this graph"); throw new
		 	MincutException("giving up on this graph"); 
		 }
		 */
		for (int i = 0; i < edges.length - 1; i++) {
			if (i >= 6) {
				throw new MincutException("giving up on this graph");
			}
			CombinationGenerator x = new CombinationGenerator(edges.length, i + 1);
			int[] indices;
			while (x.hasMore()) {
				indices = x.getNext();
				SimpleGraph<UnitNode, UnifiEdge> cloned = (SimpleGraph<UnitNode, UnifiEdge>) g
						.clone();
				ConnectivityInspector ci = new ConnectivityInspector<UnitNode, UnifiEdge>(
						cloned);
				for (int j = 0; j < indices.length; j++) {
					cloned.removeEdge((UnifiEdge) edges[j]);
				}
				if (!ci.isGraphConnected()) {
					Set<UnifiEdge> mincut = new LinkedHashSet<UnifiEdge>();
					for (int j = 0; j < indices.length; j++) {
						mincut.add((UnifiEdge) edges[j]);
					}
					return mincut;
				}
			}
		}
		throw new MincutException("No mincut here");
	}

	private static SimpleGraph generateGraphForJGraphT(UnitCollection uc, Unit srcUnit) {
		uc.checkIds();
		SimpleGraph<UnitNode, UnifiEdge> g = new SimpleGraph<UnitNode, UnifiEdge>(
				UnifiEdge.class);
		// new ClassBasedEdgeFactory<UnitNode, UnifiEdge>(UnifiEdge.class));

		Collection<Unit> explore = new ArrayList<Unit>(), next_explore = new ArrayList<Unit>();
		next_explore.add(srcUnit);
		Map<Unit, UnitNode> unitToNode = new HashMap<Unit, UnitNode>();
		while (next_explore.size() != 0) {
			explore.clear();
			explore.addAll(next_explore);
			next_explore.clear();

			for (Unit u : explore) {
				Set<Unit> s = new LinkedHashSet<Unit>();
				s.add(u);
				Collection<UnificationEvent> events = UnificationEvent
						.select_events(s);

				for (UnificationEvent e : events) {
					Util.ASSERT(e.getId() != 0);
					Unit a = e.get_unit_a();
					Unit b = e.get_unit_b();

					UnitNode n1 = unitToNode.get(a);
					if (n1 == null) {
						n1 = new UnitNode(a);
						unitToNode.put(a, n1);
					}
					UnitNode n2 = unitToNode.get(b);
					if (n2 == null) {
						n2 = new UnitNode(b);
						unitToNode.put(b, n2);
					}
					boolean added = false;
					added = g.addVertex(n1);
					if (added)
						next_explore.add(a);
					added = g.addVertex(n2);
					if (added)
						next_explore.add(b);
					g.addEdge(n1, n2, new UnifiEdge(e));
				}
			}
		}
		return g;
	}

	private static Set<UnifiEdge> fast_cut(SimpleGraph<UnitNode, UnifiEdge> g) {
		int graph_size = g.vertexSet().size();
		if (graph_size <= 6) {
			return brute_force_min_cut(g);
		}

		SimpleGraph<UnitNode, UnifiEdge> g1, g2;
		int size_limit = (int) (((double) graph_size) / Math.sqrt(2));

		g1 = contract_for_min_cut(g, size_limit);
		g2 = contract_for_min_cut(g, size_limit);

		Set<UnifiEdge> cut1 = null, cut2 = null;
		try {
			cut1 = fast_cut(g1);
			cut2 = fast_cut(g2);
		} catch (MincutException e) {
			if (cut1 == null) {
				return fast_cut(g2);
			} else {
				return cut1;
			}
		}
		if (cut1.size() < cut2.size())
			return cut1;
		else
			return cut2;
	}
	
	private static Set<UnifiEdge> heuristic_cut(SimpleGraph<UnitNode, UnifiEdge> g) {
		final int threshold=10;
		Set<UnifiEdge> weaklinks = new LinkedHashSet<UnifiEdge>();
		return weaklinks;
		/*
		int minClusterSize = g.vertexSet().size() / 15;
		if (minClusterSize < threshold) minClusterSize = threshold;
		
		if (g.vertexSet().size() <= threshold) {
			return weaklinks;
		}

		UnifiEdge weak_edge=null;
		int lastConnectedSetSize=1;
		Set<UnifiEdge> edgeSetClone = new LinkedHashSet<UnifiEdge>(g.edgeSet());
		for(UnifiEdge e:edgeSetClone) {
			UnitNode s = g.getEdgeSource(e);
			UnitNode t = g.getEdgeTarget(e);
			
			if (g.edgesOf(s).size() + g.edgesOf(t).size() >= 7) {
				continue;
			}

			g.removeEdge(e);
			ConnectivityInspector<UnitNode, UnifiEdge> ci = new ConnectivityInspector<UnitNode, UnifiEdge>((UndirectedGraph)g);			
			List<Set<UnitNode>> connectedSets = ci.connectedSets();
			if (connectedSets.size() <= lastConnectedSetSize) {
				g.addEdge(s, t, e);
				continue;
			}
			
			boolean continueFindWeakLink=false;
			for (Set<UnitNode> nodes:connectedSets) {
				if (nodes.size() <= minClusterSize) {
					continueFindWeakLink=true;
					break;
				}
			}
			if (continueFindWeakLink) {
				g.addEdge(s, t, e);
				continue;			
			}
			
			//weak link found!
			weaklinks.add(e);

			lastConnectedSetSize = connectedSets.size();
			continueFindWeakLink=true;
			for (Set<UnitNode> connSet:connectedSets) {
				if (connSet.size() <= threshold*2.5) {
					continueFindWeakLink=false;
					break;
				}
			}
			if (!continueFindWeakLink)
				break;
		}		
		
		return weaklinks;
		*/
	}

	public static Map<Unit, Set<UnifiEdge>> find_weak_link(UnitCollection uc) {
		Map<Unit, Set<UnifiEdge>> weak_link_map = new HashMap<Unit, Set<UnifiEdge>>();
		System.out.println("\n+--------------------------------------+");
		System.out.println("Finding weak links between units");
		int count=0;
		for (Unit u : uc.get_all_unique_units_sorted_by_class_size()) {
			if (uc.get_reps().get(u).size() < 10)
				break;

			System.out.println(count + ".Computing weak links for unit:" + u + " , # of unified units:" + uc.get_reps().get(u).size());
			count++;
				
			assert(u.getId() != 0);
			//uc.checkIds();
			SimpleGraph<UnitNode, UnifiEdge> g = generateGraphForJGraphT(uc, u);
			unitToGraph.put(u, g);

			try {
				//Set<UnifiEdge> weak_link = fast_cut(g);
				Set<UnifiEdge> weak_link = heuristic_cut((SimpleGraph<UnitNode, UnifiEdge>)g.clone());
				weak_link_map.put(u, weak_link);

				if (weak_link.size()> 0) {
					System.out.println("  Unit " + u + " has weak link of size:" + weak_link.size());
					int i=1;
					for (UnifiEdge e : weak_link) {
						System.out.println("    " + i + ". " + e.e);
						i++;
					}
				}
				
			} catch (MincutException e) {
				System.out.println("Can't compute min-cut for unit:" + u +
								   "probably too densly connected.");
				continue;
			}

		}

		return weak_link_map;
	}
	
	private static String[] _source_paths;

	public static void parse_source_path ()
	{
	    String path = System.getProperty ("unifi.sp");
	    if (path == null)
	    {
	        _source_paths = new String[1];
	        _source_paths[0] = ".";
	        return;
	    }

	    StringTokenizer st = new StringTokenizer (path, ":");
	    _source_paths = new String[st.countTokens()];
	    for (int i = 0 ; i < _source_paths.length ; i++)
	        _source_paths[i] = st.nextToken();
	}
	public static void main(String[] args)
	{
	    parse_source_path ();
	    
	    String uc_file = args[0];
	    UnitCollection uc = parse_outfile(uc_file);
	    Unit._current_unit_collection = uc;
	    uc.checkIds();
	    checkGraphs();	    
	    Map<Unit, Set<UnifiEdge>> weak_link_map = find_weak_link(uc);
	    
	    //String guessXMLFilename = System.getProperty("unifi.guess.filename");
	    //if (guessXMLFilename==null) guessXMLFilename="graph/guess";
	    String guessXMLFilename="graph/guess";
	    new File("graph/").mkdir();	
	    checkGraphs();
	    print_unit_graph_for_guess(uc, weak_link_map, guessXMLFilename);
	}
	private static void checkGraphs() {
		for (Unit u:unitToGraph.keySet()) {
			SimpleGraph<UnitNode, UnifiEdge> g = unitToGraph.get(u);
			
			for (UnifiEdge e:g.edgeSet()) {
				Util.ASSERT(e.e.getId()!=0);
			}
		}
	}
	private static String sanitizeForXml(Object u) {
	    String s = u.toString();
	    s = "<![CDATA[" + s.replaceAll("]]>", "]]>]]><![CDATA[") + "]]>";
	    s = s.replace((char)0xc, ' ');
	    return s;
	}
	private static String escape(Unit u) {
	    String s = u.toString();
	    s = s.replace("<", "[");
	    s = s.replace(">", "]");
	    s = s.replace("\"", ".");
	    s = s.replace("'", ".");
	    s = s.replace("/", ".");
	    s = s.replace("*", "STAR");
	    s = s.replace("&", "AND");
	    s = s.replace((char)0xc, ' ');
	    return s;
	}
	private static void print_unit_graph_for_guess(UnitCollection uc, Map<Unit, Set<UnifiEdge>> weak_link_map, 
                                                       String filenameBase) {
		int fileidx = 0;
	    for (Unit current_unit:uc.get_all_unique_units_sorted_by_class_size()) {
	    	if ( !unitToGraph.keySet().contains(current_unit) ) {
	    		continue;
	    	}
			//Map<Unit, Integer> unitToId = new LinkedHashMap<Unit, Integer>();
			int i;
	    	PrintWriter out;
	    	try {
	    		String graphMlFilename = fileidx + "-" + current_unit;
	    		int tmpidx = graphMlFilename.length()-1 >= 80 ? 80:graphMlFilename.length()-1;
	    		graphMlFilename = graphMlFilename.substring(0, tmpidx) + ".xml";
	    		graphMlFilename = graphMlFilename.trim();
	    		graphMlFilename = graphMlFilename.replace("/", ".");
	    		graphMlFilename = graphMlFilename.replace(" ", "-");
	    		graphMlFilename = graphMlFilename.replace("(", "").replace(")","").replace("[","").replace("]","");
	    		fileidx++;
	    		out = new PrintWriter (new FileOutputStream(filenameBase + graphMlFilename));	    		
	    	} catch (FileNotFoundException e) {
			// 	TODO Auto-generated catch block
	    		e.printStackTrace();
	    		System.err.println(filenameBase + "-" + current_unit + ".xml");
	    		return;
	    	}    	
	    
	    	out.println (
	    			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
	    			"<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n" + 
	    			"  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + 
	    			"  xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n" + 
	    			"  http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n" + 
	    			
	    			"  <key id=\"classname\" for=\"node\" attr.name=\"classname\" attr.type=\"string\"/>\n" + 
	    			"  <key id=\"type\" for=\"node\" attr.name=\"type\" attr.type=\"string\"/>\n" + 
	    			"  <key id=\"uid\" for=\"node\" attr.name=\"uid\" attr.type=\"string\"/>\n" + 
	    			"  <key id=\"classname\" for=\"edge\" attr.name=\"classname\" attr.type=\"string\"/>\n" + 
	    			"  <key id=\"type\" for=\"edge\" attr.name=\"type\" attr.type=\"string\"/>\n" +
	    			"  <key id=\"eid\" for=\"edge\" attr.name=\"eid\" attr.type=\"string\"/>\n" +
	    			"  <key id=\"labelcolor\" for=\"edge\" attr.name=\"color\" attr.type=\"string\"/>\n");
	    	
	    	
	    	out.println ("<graph edgedefault=\"undirected\">\n");
	    
	    	SimpleGraph<UnitNode, UnifiEdge> g = unitToGraph.get(current_unit);
	    	assert(g != null);
	    	i=0;
	    	for (UnitNode v:g.vertexSet()) {
	    		//assert(unitToId.get(v.u)!=null);
	    		//unitToId.put(v.u, i);
	    		//out.print ("<node id=\"" + "n"+i + "\">\n");    
	    		out.print ("<node id=\"" + escape(v.u)+ "\">\n");
	            out.print ("<data key=\"classname\">" + sanitizeForXml(v.u)  + "</data>\n");
	            out.print ("<data key=\"uid\">" + v.u.getId()  + "</data>\n");
	            Util.ASSERT(v.u.getId()!=0, "unit:"+v.u+" has 0 as its id");
	            out.print ("<data key=\"type\">nonexec</data>\n");
	            out.print ("</node>\n");
	            i++;
	    	}
	    	
	    	Set<UnifiEdge> weaklink = weak_link_map.get(current_unit);
	    	assert(weaklink != null);
	    	int edgeId=0;	    	
	    	for (UnifiEdge e:g.edgeSet()) {
	    		UnitNode s = g.getEdgeSource(e);
	    		UnitNode t = g.getEdgeTarget(e);
	    		//assert(unitToId.get(s.u)!=null);
	    		//assert(unitToId.get(t.u)!=null);
	    		//int id1 = unitToId.get(s.u);
	    		//int id2 = unitToId.get(t.u);
	        	//out.print ("<edge id=\"" + edgeId + "\" source=\"" + "n"+id1   
	                    //+ "\" target=\"" + "n"+id2 + "\">\n");
	        	out.print ("<edge id=\"" + edgeId + "\" source=\"" + escape(s.u)    
	        			+ "\" target=\"" + escape(t.u) + "\">\n");
	            out.print ("<data key=\"classname\">"+sanitizeForXml(e.e) + "</data>\n");
	            Util.ASSERT(e.e.getId()!=0, "event:"+e.e+" has 0 as its id");
	            out.print ("<data key=\"eid\">" + e.e.getId() + "</data>\n");
	            out.print ("<data key=\"type\">nonexec</data>\n");
	            
	            boolean is_weakedge=false;
	            for (UnifiEdge weakedge:weaklink) {
	            	if (weakedge.e.equals(e.e)) {
	            		out.print ("<data key=\"labelcolor\">red</data>\n");
	            		is_weakedge=true;
	            		break;
	            	}
	            }
	            if (!is_weakedge) {
	            	out.print ("<data key=\"labelcolor\">green</data>\n");
	            }
	            out.print ("</edge>\n");
	            
	            edgeId++;
	    	}
	    	out.println ("</graph>\n</graphml>\n");
	    	out.close();
	    
	    }
	
	}

	public static UnitCollection parse_outfile(String uc_file) {
		ObjectInputStream ois;
		UnitCollection uc=null;
		try {
			ois = new ObjectInputStream(new FileInputStream(uc_file));
		} catch (Exception e) {
			System.err.println("Warning: error opening file: " + uc_file);
			return null;
		}

		try {
			uc = (UnitCollection) ois.readObject();
			ois.close();
		} catch (Exception e) {
			System.err.println("Warning: error reading data from file " + uc_file);
			System.err.println(e);
			Util.ASSERT(false);
		}

		uc.compute_reps();
		return uc;
	}
}

class CombinationGenerator {

	private int[] a;
	private int n;
	private int r;
	private BigInteger numLeft;
	private BigInteger total;

	public CombinationGenerator(int n, int r) {
		if (r > n) {
			throw new IllegalArgumentException();
		}
		if (n < 1) {
			throw new IllegalArgumentException();
		}
		this.n = n;
		this.r = r;
		a = new int[r];
		BigInteger nFact = getFactorial(n);
		BigInteger rFact = getFactorial(r);
		BigInteger nminusrFact = getFactorial(n - r);
		total = nFact.divide(rFact.multiply(nminusrFact));
		reset();
	}

	public void reset() {
		for (int i = 0; i < a.length; i++) {
			a[i] = i;
		}
		numLeft = new BigInteger(total.toString());
	}

	// ------------------------------------------------
	// Return number of combinations not yet generated
	// ------------------------------------------------

	public BigInteger getNumLeft() {
		return numLeft;
	}

	// -----------------------------
	// Are there more combinations?
	// -----------------------------

	public boolean hasMore() {
		return numLeft.compareTo(BigInteger.ZERO) == 1;
	}

	// ------------------------------------
	// Return total number of combinations
	// ------------------------------------

	public BigInteger getTotal() {
		return total;
	}

	// ------------------
	// Compute factorial
	// ------------------
	private static BigInteger getFactorial(int n) {
		BigInteger fact = BigInteger.ONE;
		for (int i = n; i > 1; i--) {
			fact = fact.multiply(new BigInteger(Integer.toString(i)));
		}
		return fact;
	}

	// --------------------------------------------------------
	// Generate next combination (algorithm from Rosen p. 286)
	// --------------------------------------------------------
	public int[] getNext() {
		if (numLeft.equals(total)) {
			numLeft = numLeft.subtract(BigInteger.ONE);
			return a;
		}
		int i = r - 1;
		while (a[i] == n - r + i) {
			i--;
		}
		a[i] = a[i] + 1;
		for (int j = i + 1; j < r; j++) {
			a[j] = a[i] + j - i;
		}
		numLeft = numLeft.subtract(BigInteger.ONE);
		return a;
	}
}
