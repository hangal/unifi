/*
UniFi software.
Copyright [2001-2010] Sudheendra Hangal  

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 
    http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/

package fieldseekinganalysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.Type;

import unifi.UnificationEvent;
import unifi.UnitCollection;
import unifi.solver.Constraint;
import unifi.solver.ConstraintSet;
import unifi.units.FieldUnit;
import unifi.units.LocalVarUnit;
import unifi.units.MethodParamUnit;
import unifi.units.PhiUnit;
import unifi.units.ReturnValueUnit;
import unifi.units.Unit;
import unifi.util.Util;

/** this class implementes the field-seeking params and retvals analysis.
 * note this analysis was used when we didn't have context-sensitive analysis.
 * this class is not used any more. */
public class FindPolymorphicMethods {

private static List<Node<Unit>> all_nodes = new ArrayList<Node<Unit>>();
private static Map<Unit, Node<Unit>> units_to_node_map = new LinkedHashMap<Unit, Node<Unit>>();
private static Collection<UnificationEvent> events;

private static final int BLACK = 1;
private static final int GREY = 2;
private static final int WHITE = 3;
private static final boolean only_prims = (System.getProperty("unifi.track.references") == null);

/** recomputes all edges from events collection (unit b of event -> unit a of event) */
private static void set_b_to_a_edges()
{
    for (Node<?> n: all_nodes)
        n.clear_edges();

    for (UnificationEvent u: events)
    {
        Unit a = u.get_unit_a();
        Unit b = u.get_unit_b();
        Node<Unit> a_node = units_to_node_map.get(a);
        Node<Unit> b_node = units_to_node_map.get(b);
        if ((a_node == null) || (b_node == null))
        {
            System.out.println ("WARNING: a = " + a + " b = " + b);
            if (a_node == null) System.out.println ("a node = null");
            if (b_node == null) System.out.println ("b node = null");
            Util.die ();
            continue;
        }

        b_node.add_edge (a_node);
    }
}

/** recomputes all edges from events collection (unit b of event -> unit a of event) */
private static void set_a_to_b_edges()
{
    for (Node n: all_nodes)
        n.clear_edges();

    for (UnificationEvent u: events)
    {
        Unit a = u.get_unit_a();
        Unit b = u.get_unit_b();
        Node<Unit> a_node = units_to_node_map.get(a);
        Node<Unit> b_node = units_to_node_map.get(b);
        if ((a_node == null) || (b_node == null))
        {
            System.out.println ("WARNING: a = " + a + " b = " + b);
            Util.die ();
            continue;
        }
        a_node.add_edge (b_node);
    }
}


/** takes a unit collection as input and returns a new unit collection
    with units and unification events. */
public static UnitCollection eliminatePolymorphism(UnitCollection uc_input)
{
    Collection<Unit> units = uc_input.get_units();
    Collection<UnificationEvent> events1 = uc_input.get_events();
    FindPolymorphicMethods.events = events1;

    for (Unit u : units)
    {
        Node<Unit> n = new Node<Unit>(u);
        all_nodes.add(n);
        units_to_node_map.put (u, n);
        Util.ASSERT (units_to_node_map.get(u) != null);
    }

    for (UnificationEvent event: events1)
    {
        Util.ASSERT (units.contains(event.get_unit_a()));       
        Util.ASSERT (units.contains(event.get_unit_b()));       
    }

    set_b_to_a_edges();

    color_all_nodes(WHITE);
    for (Node<Unit> n : all_nodes)
        dfs_flows_to_field (n);

    set_a_to_b_edges();
    color_all_nodes(WHITE);
    for (Node<Unit> n : all_nodes)
        dfs_flows_from_field (n);

    print_polymorphism_data();

    set_b_to_a_edges();
    UnitCollection uc = generate_equiv_classes();
    fixConstraints(uc, uc_input.constraint_set());
    return uc;
}

/** copies constraints from old_cs into unit collection, filtering 
to retain only those constraints all of whose units are in uc's units. */
private static void fixConstraints(UnitCollection uc, ConstraintSet old_cs)
{
    ConstraintSet new_cs = new ConstraintSet();

    for (Constraint c: old_cs.constraints())
        new_cs.add_constraint(c);
    new_cs.delete_constraints_not_involving(uc.get_units());
    uc.set_constraint_set(new_cs);
}

private static boolean is_prim_or_string_type (Unit u)
{
    Type t = u.getType();
    if (t == null)  // not quite sure when t is null ? perhaps for local vars...
        return false;
    return ((t instanceof BasicType) || t.getSignature().equals("Ljava/lang/String;"));
}

private static boolean consider_node(Node<Unit> n)
{
    Unit u = n.data();
    if ((u instanceof LocalVarUnit) && (u.getType() == null)) // don't always know local var type, give benefit of doubt
        return true;
    if (u instanceof PhiUnit) // always consider phi nodes
        return true;

    if (only_prims)
        if (!is_prim_or_string_type(u))
            return false;

    if ((u instanceof MethodParamUnit) && (!n.flows_to_field))
        return false;
    if ((u instanceof ReturnValueUnit) && (!n.flows_from_field))
        return false;

    return true;
}

// eliminate polymorphism and generate equiv classes, using only considered units and events
private static UnitCollection generate_equiv_classes()
{
    for (Node<Unit> n : all_nodes)
        n.data().reset();

    Set<Unit> considered_units_set = new LinkedHashSet<Unit>();

    for (Node<Unit> n : all_nodes)
    {
        if (!consider_node (n))
            continue;

        considered_units_set.add(n.data());
    }

    print_discarded_mp_rv_stats();

    // considered events are those involving 2 considered units
    List<UnificationEvent> considered_events = new ArrayList<UnificationEvent>();
    for (UnificationEvent e : events)
    {
        Unit a = e.get_unit_a();
        Unit b = e.get_unit_b();
        if (considered_units_set.contains(a) && considered_units_set.contains(b))
            considered_events.add(e);
    }

    // remove each event that depends on another event that is not considered.
    // actually we should implement this using fixed point or recursion. but right now
    // we just do one pass - we essentially eliminate length units of params to poly methods.
    for (Iterator<UnificationEvent> it = considered_events.iterator(); it.hasNext(); )
    {
        UnificationEvent ue = it.next();
        UnificationEvent depends_on = ue.depends_on();
        if ((depends_on != null) && !considered_events.contains(depends_on))
            it.remove();
    }

    // each unit has a list of all unif events it is involved with, recompute this and unify units
    for (UnificationEvent e : considered_events)
    {
        e.get_unit_a().addUnificationEvent(e);
        e.get_unit_b().addUnificationEvent(e);
    }

    for (UnificationEvent e : considered_events)
    {
        Unit a = e.get_unit_a();
        Unit b = e.get_unit_b();
        a.unify(b);
    }

    UnitCollection uc = new UnitCollection(considered_units_set, considered_events);
    return uc;
}

/** compute and print stats */
private static void print_polymorphism_data()
{
    System.out.println ("---------- Begin polymorphism data ------------");
    int poly_param_count = 0, nonpoly_param_count = 0;
    int poly_retval_count = 0, nonpoly_retval_count = 0;

    for (Node<Unit> n : all_nodes)
    {
        Unit u = n.data();
        if (!is_prim_or_string_type(u))
            continue;

        if (u instanceof MethodParamUnit)
        {
            MethodParamUnit mpu = (MethodParamUnit) u;
            if (!mpu.is_in_analyzed_code()) 
            {
                // we don't have a model for library methods, so will always not be field-seeking
                Util.ASSERT (!n.flows_to_field());
                continue;
            }

            if (!n.flows_to_field())
            {
                System.out.println ("May be polymorphic: " + mpu.full_toString());
                poly_param_count++;
            }
            else
            {
                System.out.println ("May NOT be polymorphic: " + mpu.full_toString());
                nonpoly_param_count++;
            }
        }

        if (u instanceof ReturnValueUnit)
        {
            ReturnValueUnit rvu = (ReturnValueUnit) u;
            if (!rvu.is_in_analyzed_code()) // skip library methods
            {
                if (n.flows_from_field())
                    Util.die ("Unexpected flow into RV of library method = " + u);
                Util.ASSERT (!n.flows_from_field());
                // we don't have a model for library methods, so will always not be field-seeking
                
                continue;
            }

            if (!n.flows_from_field())
            {
                System.out.println ("May be polymorphic: " + rvu.full_toString());
                poly_retval_count++;
            }
            else
            {
                System.out.println ("May NOT be polymorphic: " + rvu.full_toString());
                nonpoly_retval_count++;
            }
        }
    }

    System.out.println ("---------- End polymorphism data ------------");
    int total_params = nonpoly_param_count + poly_param_count;
    int total_retvals = nonpoly_retval_count + poly_retval_count;

    if ((total_params > 0) && (total_retvals > 0))
    {
        System.out.println ("Non polymorphic parameters: " + nonpoly_param_count + " of " + total_params + "(" + (int) (100*(double) nonpoly_param_count)/total_params + "%)");
        System.out.println ("Non polymorphic return values: " + nonpoly_retval_count + " of " + total_retvals + "(" + (int) (100*(double) nonpoly_retval_count)/total_retvals + "%)");
        System.out.println ("---------------------------------------------");
    }
}

/** print stats for why params/retvals were considered poly */
private static void print_discarded_mp_rv_stats()
{
    int n_flows_to_field = 0, n_doesnt_flow_to_field = 0; 
    int n_only_lib_flow = 0, n_no_flow_nodes = 0;
    int n_one_out_but_not_mp = 0; // one outgoing edge but not to a method param
    int n_one_out_to_mp_non_lib = 0; // one outgoing edge to a method param but not a lib

    for (Node<Unit> n : all_nodes)
    {
        Unit u = n.data();
        if (only_prims)
            if (!is_prim_or_string_type(u))
                continue;
        
        if (!(u instanceof MethodParamUnit))
            continue;

        MethodParamUnit mp = (MethodParamUnit) u;

        if (!mp.is_in_analyzed_code())
            continue;

        if (!n.flows_to_field)
        {
            n_doesnt_flow_to_field++;
            List<Node<Unit>> nodes = n.to_nodes();
            if (nodes.size() == 0)
            {
                System.out.println ("METHODPARAM: No outgoing edge: " + u);
                n_no_flow_nodes++;
            }
            else if (nodes.size() == 1)
            {
                Unit next_unit = nodes.get(0).data();
                if (!(next_unit instanceof MethodParamUnit))
                {
                    n_one_out_but_not_mp++;
                    System.out.println ("METHODPARAM: Only one outgoing edge for " + u + " but flows to " + next_unit);
                    continue;
                }
                MethodParamUnit mp_next_unit = (MethodParamUnit) next_unit;
                if (!mp_next_unit.is_in_analyzed_code())
                {
                    System.out.println ("METHODPARAM: passed to library: " + u);
                    n_only_lib_flow++;
                }
                else
                {
                    n_one_out_to_mp_non_lib++;
                    System.out.println ("METHODPARAM: Only one outgoing edge to method param for " + u + " but not library: " + next_unit);
                }
            }
            else
                System.out.println ("METHODPARAM: # of to nodes = " + nodes.size());
        }
        else
            n_flows_to_field++;


/*
        if ((u instanceof ReturnValueUnit) && !n.flows_from_field)
        {
            List<Node<Unit>> nodes = n.from_nodes();
            if (nodes.size() == 0)
                System.out.println ("RETVAL disconnected: " + u);
            if (nodes.size() == 1)
            {
                Unit prev_unit = nodes.get(0).data();
                if (!(prev_unit instanceof ReturnValueUnit))
                    continue;
                ReturnValueUnit rv_prev_unit = (ReturnValueUnit) prev_unit;
                if (!rv_prev_unit.is_in_analyzed_code())
                    System.out.println ("RETVAL connected only to library: " + u);
            }
            else
                System.out.println ("METHODPARAM: # of to nodes = " + nodes.size());
        }
*/
    }
    int total_mps = n_flows_to_field + n_doesnt_flow_to_field;
    if (total_mps == 0)
    {
        System.out.println ("0 Method Parameters!");
        return;
    }

    System.out.println ("Method parameters (primitives and strings): " + total_mps);
    int flows_to_field_pct = (100 * n_flows_to_field) / total_mps;
    System.out.println ("Field-seeking: " + n_flows_to_field + " (" + flows_to_field_pct + "%)");
    int no_flow_pct = 100 * n_no_flow_nodes / total_mps;
    System.out.println ("no outflow: " + n_no_flow_nodes + " (" + no_flow_pct + "%)");
    int n_only_lib_flow_pct = 100 * n_only_lib_flow / total_mps;
    System.out.println ("only flow to library: " + n_only_lib_flow + " (" + n_only_lib_flow_pct + "%)");

    int one_out_but_not_mp_pct = 100 * n_one_out_but_not_mp / total_mps;
    System.out.println ("exactly one out but not method param: " + n_one_out_but_not_mp + " (" + one_out_but_not_mp_pct + "%)");
    int one_out_to_mp_non_lib_pct = 100 * n_one_out_to_mp_non_lib / total_mps;
    System.out.println ("exactly one out to a method param, but not library: " + n_one_out_to_mp_non_lib + " (" + one_out_to_mp_non_lib_pct + "%)");
}

///////////////////////// graph methods ///////////////////////////////

private static void dfs_set_rep(Node<Unit> n, Node<Unit> rep)
{
    int color = n.color();

    if (color == BLACK)
        return;

    if (color == GREY)
        return;

    n.set_color(GREY);
    n.set_rep (rep);
    for (Node<Unit> n1 : n.to_nodes())
        dfs_set_rep (n1, rep);

    n.set_color(BLACK);
}

private static void dfs_flows_from_field (Node<Unit> n)
{
    int color = n.color();

    if (color == BLACK)
        return;

    if (color == GREY)
        return; // already being visited

    if (n.data() instanceof FieldUnit)
        n.set_flows_from_field();
        
    n.set_color(GREY);
    for (Node<Unit> n1 : n.to_nodes())
    {
        // why is the following check present ?
        // because we dont want to propagate beyond input params,
        // otherwise we'll detect spurious flow into return values
        // e.g. foo(FIELD1); 
        // e.g. foo(FIELD2); 
        // int foo(x) { return x+1; }
        // return value of foo will be detected as non-polymorphic,
        // when in fact it is polymorphic.
        if (n1.data() instanceof MethodParamUnit)
            continue;
        dfs_flows_from_field (n1);
        if (n1.flows_from_field())
            n.set_flows_from_field();
    }
    n.set_color(BLACK);
}

private static void dfs_flows_to_field (Node<Unit> n)
{
    int color = n.color();

    if (color == BLACK)
        return; // already visited
    if (color == GREY)
        return; // already being visited

    if (n.data() instanceof FieldUnit)
        n.set_flows_to_field();
        
    n.set_color(GREY);
    for (Node<Unit> n1 : n.to_nodes())
    {
        dfs_flows_to_field (n1);
        // do not want to follow through return values, because they may end up in a field
        // e.g. FIELD = foo(FIELD);
        // in this case, foo's param is polymorphic
        if (n1.data() instanceof ReturnValueUnit)
            continue;
        if (n1.flows_to_field())
            n.set_flows_to_field();
    }
    n.set_color(BLACK);
}

private static void color_all_nodes (int c)
{
    for (Node<Unit> n : all_nodes)
        n.set_color (c);
}

private static class Node<T> {
    private boolean flows_to_field = false;
    private boolean flows_from_field = false;
    private int color;
    private List<Node<T>> to = new ArrayList<Node<T>>();
    private T data;
    private Node<T> rep;

    public Node(T t) { data = t; } 

    public void clear_edges () { to.clear(); }
    public void add_edge (Node<T> n) { to.add (n); }
    public List<Node<T>> to_nodes () { return to; }
    public void set_color (int c) { color = c; }
    public int color () { return color; }
    public T data() { return data; }

    public boolean flows_to_field () { return flows_to_field; }
    public void set_flows_to_field() { flows_to_field = true; }
    public boolean flows_from_field () { return flows_from_field; }
    public void set_flows_from_field() { flows_from_field = true; }
    public Node<T> rep() { return rep; }
    public void set_rep(Node<T> n) { rep = n; }
}
}

