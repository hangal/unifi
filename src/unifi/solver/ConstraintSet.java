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

package unifi.solver;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import unifi.units.MultUnit;
import unifi.units.Unit;
import unifi.util.Util;

/** core data structure for holding the entire compound constraint set for multiply/divides.  */

public class ConstraintSet implements Serializable {

	private static Logger logger = Logger.getLogger("unifi.constraints");

/** ConstraintSet can have duplicate constraints -
 * this should't matter for correctness, but
 * we could eliminate duplicates for performance.
 */
private Collection<Constraint> _c = new ArrayList<Constraint>();

/** used only for printing units involved in this constraint set. */
private List<Unit> _sorted_all_units;

// unit->constraint which defines that unit
private Map<Unit,Constraint> _formulas = new LinkedHashMap<Unit, Constraint>();

/** this is mapping unit u -> collection of constraints with unit u (incl. formula for u ?) */
private Map<Unit,Collection<Constraint>> _units_to_constraints_map = new LinkedHashMap<Unit,Collection<Constraint>>();

public Map<Unit,Constraint> formulas() { return _formulas; }

public Collection<Constraint> constraints() { return _c; }

// recomputes unit order, placing non preferred units after preferred units
private void recompute_units_order(Collection<Unit> preferred_units)
{
    Set<Unit> all_units = new LinkedHashSet<Unit>();

    // collect all_units first
    for (Constraint c : _c)
    {
        for (Iterator<Dimension> it = c.dimensions().iterator(); it.hasNext();)
        {
            Dimension d = it.next();
            all_units.add(d.unit());
            Util.ASSERT(all_units.contains(d.unit()));
        }
    }

    // compute _sorted_all_units from all_units
    List<Unit> list = new ArrayList<Unit>();
    list.addAll (all_units);
    Collections.sort (list, new StableComparator(preferred_units));
    Collections.reverse (list);
    _sorted_all_units = list;
    logger.info ("After sorting units for constraint solving: " + short_toString());
}

/** retain only those constraints which contain only units from the given units collection.
 */
public void delete_constraints_not_involving(Collection<Unit> units)
{
    outer:
    for (Iterator<Constraint> it = _c.iterator(); it.hasNext(); )
    {
        for (Iterator<Dimension> it_d = it.next().dimensions().iterator(); it_d.hasNext(); )
        {
            Dimension d = it_d.next();
            if (!units.contains (d.unit()))
            {
                it.remove ();
                continue outer;
            }
        }
    }

	// just so we recompute _sorted_all_units and get rid of units in it that don't belong in the current constraint set
	recompute_units_order(units);
	recompute_units_to_constraints_map();
	logger.info ("After deleting constraints: " + short_toString());
}

public List<Unit> sorted_all_units()
{
    if (_sorted_all_units == null)
        recompute_units_order(null);

    return _sorted_all_units;
}

public Map<Unit,Collection<Constraint>> map() { return _units_to_constraints_map; }

// add the mapping u->c in the units to constraints map
private void units_to_constraints_map_add (Unit u, Constraint c)
{
    List a = (List) _units_to_constraints_map.get (u);
    if (a == null)
    {
        a = new ArrayList<Constraint>();
        _units_to_constraints_map.put (u, a);
    }

    if (!a.contains (c))
        a.add (c);
}

// remove the mapping u->c from _units_to_constraints_map
private void units_to_constraints_map_remove (Unit u, Constraint c)
{
    Collection a = _units_to_constraints_map.get (u);
    Util.ASSERT (a != null, "unit not in hashmap!");
    if (!a.contains(c))
    {
    	System.err.println ("a.contains(c) failed\nu = " + u + "\nc = " + c + "\n");
    	System.err.println ("a has the following " + a.size() + " elements");
    	for (Object x : a)
        	System.err.println (x);
    	Util.die();
    }
	a.remove (c);

}

/**
 * recomputes the units U -> list of constraints which involve U map.
 */
private void recompute_units_to_constraints_map()
{
    _units_to_constraints_map.clear();

    for (Constraint c : _c)
    {
        Dimensions dims = c.dimensions();
        for (Iterator<Dimension> it = dims.iterator(); it.hasNext(); )
        {
            Dimension d = it.next();
            units_to_constraints_map_add (d.unit(), c);
        }
    }
}

// removes empty constraints, recomputes the units_to_constraints map
private void remove_empty_constraints()
{
    // remove empty constraints
    for (Iterator<Constraint> it = _c.iterator(); it.hasNext(); )
    {
        Constraint c = it.next();
        if (c.dimensions().get_n_dimensions() == 0)
            it.remove();
    }

    recompute_units_to_constraints_map();
}

/** flattens all the constraints by replacing MultUnits
    in constraints with an equivalent unit. removes empty
    constraints.
    note: there may still be duplicate constraints.
 */
private void flatten()
{
    for (Constraint c : _c)
        c.flatten();

    remove_empty_constraints();
    recompute_units_to_constraints_map();
}

/**
 * updates all constraints in this set by replacing
 * each unit U with unit V if there exists a mapping
 * U->V in replace_map. The sets of U's and V's must be disjoint.
 * also recomputes _units_to_constraints_map.
 */
private void rewrite_constraints (Map<Unit, Unit> replace_map)
{
    Util.ASSERT (_units_to_constraints_map != null);

    // First verify that no value is also a key
    // extract values into a set
    Collection<Unit> values = replace_map.values();
    Set<Unit> value_set = new HashSet<Unit>();
    value_set.addAll(values);
    // intersect value set with key set and ensure intersection is empty
    Set<Unit> key_set = replace_map.keySet();
    value_set.retainAll(key_set);
    Util.ASSERT (value_set.size() == 0);

    for (Map.Entry<Unit, Unit> me : replace_map.entrySet())
    {
        Unit orig = me.getKey();
        Unit repl = me.getValue();
        Util.ASSERT (orig != null);
        Util.ASSERT (repl != null);

        // the _units_to_constraints_map will get out of sync
        // during the execution of this loop, after the constraint
        // is rewritten but that's ok, we'll recompute it
        Collection<Constraint> constraints = _units_to_constraints_map.get(orig);
        if (constraints != null)
            for (Constraint c : constraints)
                c.dimensions().rewrite(orig, new Dimensions(repl));
    }

    remove_empty_constraints();
    recompute_units_to_constraints_map();
}

// compute the _preferred_rep_map (it does not include
// mapping from a unit to itself). the preferred_rep for
// every unit is the most stable unit
// from the equivalence class it belongs to.
private Map<Unit, Unit> compute_preferred_rep_map(Map<Unit,List<Unit>> reps, Collection<Unit> stable_units)
{
//    preferred_rep_map.clear();

    Map<Unit, Unit> preferred_rep_map = new LinkedHashMap<Unit, Unit>();

    for (Map.Entry<Unit,List<Unit>> me : reps.entrySet())
    {
        List<Unit> list = me.getValue();

        // ensure there are no duplicates.
        Set<Unit> s = new LinkedHashSet<Unit>();
        s.addAll(list);
        Util.ASSERT (s.size() == list.size());

        // sort the list
        List<Unit> tmp_list = new ArrayList<Unit>();
        tmp_list.addAll(list);
        Collections.sort (tmp_list, new StableComparator(stable_units));

        // the first unit is the best unit for the rep
        Unit first_unit = null;
        for (Unit u : tmp_list)
        {
            if (first_unit == null)
                first_unit = u;
            else
                preferred_rep_map.put (u, first_unit);
        }
    }

    int i = 0;
    if (logger.isLoggable(Level.FINE))
    	for (Unit u : preferred_rep_map.keySet())
    		if (u instanceof MultUnit)
    			logger.fine (++i + ". Mult unit " + u + " maps to " + preferred_rep_map.get(u));

    return preferred_rep_map;
}

/** prepares the constraint set for solving.
    preferred reps is a mapping U->V where U is the most stable representative for U
    given the unification constraints.
   preferred_units may be null, all units in preferred_units are more stable than any unit which is not in preferred_units
*/
public void prepare_to_solve(Map<Unit,List<Unit>> reps, Collection<Unit> preferred_units)
{
    _formulas.clear();
    verify();

    Map<Unit, Unit> preferred_rep_map = compute_preferred_rep_map (reps, preferred_units);
    logger.info (_c.size() + " constraints before rewriting compound constraints with preferred units");
    rewrite_constraints (preferred_rep_map);
    logger.info (_c.size() + " constraints after rewriting compound constraints with preferred units and before flattening");
    flatten();
    logger.info (_c.size() + " constraints after flattening compound constraints");

    // after flattening there shd be no mult units left.
    for (Constraint c : _c)
    {
        for (Dimension d : c.dimensions())
            Util.ASSERT (!(d.unit() instanceof MultUnit));
        c.verify();
    }

    recompute_units_order(preferred_units);

    verify();
}

// add a constraint u = u1 * u2^coeff
// also adds u, u1, u2 to the set _all_units if they don't already exist.
public void add_constraint (Unit u, Unit u1, Unit u2, Fraction coeff)
{
    add_constraint (u, new Dimensions (u1, u2, coeff));
}

// add a constraint u = d
private void add_constraint (Unit u, Dimensions d)
{
    Constraint c = new Constraint (u, d);
//    System.out.println ("new compound constraint: " + c);
    add_constraint (c);
}

// adds a constraint, including setting up
// the unit->constraint hashmap's.
public void add_constraint (Constraint c)
{
    if (_c.contains (c))
    {
        System.out.println ("OK, we got a duplicate constraint: " + c);
        return;
    }

    _c.add (c);
    for (Iterator it = c.dimensions().iterator(); it.hasNext(); )
    {
        Dimension dim = (Dimension) it.next();
        units_to_constraints_map_add (dim.unit(), c);
    }
}

// delete a constraint from the system,
// also update maps
// does *not* update the all_units collection
public void delete_constraint (Constraint c)
{
    System.out.println ("deleting constraint " + c);
    Util.ASSERT (_c.contains(c));
    _c.remove (c);
    for (Iterator it = c.dimensions().iterator(); it.hasNext(); )
    {
        Dimension dim = (Dimension) it.next();
        units_to_constraints_map_remove (dim.unit(), c);
    }
}

// add the formula for unit u according to constraint c
public void add_formula (Unit u, Constraint c)
{
    // formula for u must not already exist
    Util.ASSERT (_formulas.get(u) == null);
    _formulas.put (u, c);
    c.set_is_formula (true);
}

// rewrites all constraints involving u with d
// (except for the one this unit maps to in the formulas map)
public void rewrite_unit (Unit u, Dimensions dims)
{
    Constraint formula = _formulas.get (u);
    /*
    Util.ASSERT (formula != null, "rewriting unit without deriving its formula ?");
    Util.ASSERT (!dims.is_derived_from(u), "rewriting unit with a dimension including itself");
    */

    System.out.println ("about to replace " + u + " with " + dims);

    Collection<Constraint> a = _units_to_constraints_map.get(u);
    if (a == null)
        return;

    Collection<Constraint> constraints_to_be_rewritten = new ArrayList<Constraint>();
    constraints_to_be_rewritten.addAll (a);

    // make sure no duplicates
    Set<Constraint> tmp = new LinkedHashSet<Constraint>();
    tmp.addAll (a);
    Util.ASSERT (tmp.size() == constraints_to_be_rewritten.size());

    for (Constraint c : constraints_to_be_rewritten)
    {
        if (c == formula)
            continue;

        Dimensions d = c.dimensions();
        // update the map, deleting the old dimensions...
        for (Iterator it = d.iterator(); it.hasNext(); )
        {
            Dimension dim = (Dimension) it.next();
            units_to_constraints_map_remove (dim.unit(), c);
        }

        // rewrite the dims
        d.rewrite (u, dims);

        // update the map again, adding the new dims to u->c
        for (Iterator it = d.iterator(); it.hasNext(); )
        {
            Dimension dim = (Dimension) it.next();
            units_to_constraints_map_add (dim.unit(), c);
        }

        if (d.get_n_dimensions() == 0)
            delete_constraint (c);

        // should also check if this rewrite results in a duplicate constraint
        // (and delete if so) ??
    }
}

// verify the maps of this constraint set
public void verify ()
{
    // must not have any empty constraints
    for (Constraint c : _c)
        Util.ASSERT (c.dimensions().get_n_dimensions() > 0);

    // check consistency of _units_to_constraints_map:
    // every constraint that a unit U maps to must be derived from U
    for (Map.Entry<Unit, Collection<Constraint>> me :  _units_to_constraints_map.entrySet())
    {
        Unit u = me.getKey();
        Collection<Constraint> a = me.getValue ();
        for (Constraint c : a)
        {
            if (!c.dimensions().is_derived_from(u))
            {
                System.out.println ("FAILING Unit: " + u);
                System.out.println ("FAILING Constraint: " + c);
                Util.die();
            }
        }
    }

    // check consistency of _units_to_constraints_map: every constraint must
    // be present in the collection of constraints that each of it's units maps to
    for (Iterator it = _c.iterator(); it.hasNext(); )
    {
        Constraint c = (Constraint) it.next();
        Dimensions d = c.dimensions();
        for (Iterator it1 = d.iterator(); it1.hasNext(); )
        {
            Dimension dim = (Dimension) it1.next();
            Collection<Constraint> al = _units_to_constraints_map.get (dim.unit());
            Util.ASSERT (al.contains(c));
        }
    }

    // a constraint can't be the formula for more than one unit
    int n_formulas = _formulas.keySet().size();
    Set<Constraint> s = new LinkedHashSet<Constraint>();
    s.addAll(_formulas.values());
    // if there are any duplicates in _formulas.values(), size of set s
    // will be different from n_formulas
    Util.ASSERT (s.size() == n_formulas);

    // a formula for a unit must contain the unit itself.
    for (Map.Entry<Unit, Constraint> me : _formulas.entrySet())
    {
        Unit u = me.getKey();
        Constraint c = me.getValue();
        Util.ASSERT (c.dimensions().exponent_of(u) != null);
    }
}

// marks units as dimensionless where a constraint
// consists of just that unit
public void mark_dimensionless_units ()
{
    for (Constraint c : _c)
    {
        Dimensions dims = c.dimensions();

        if (dims.get_n_dimensions() == 1)
        {
           // this is a 1 dimension constraint, so that dimension must
           // be dimensionless
            Iterator it1 = dims.iterator();
            Dimension dim = (Dimension) it1.next();
            Util.ASSERT (!it1.hasNext(), "More than 1 dimension when n_dimensions == 1!");
            dim.unit().set_is_dimension_less(true);
        }
    }
}

// prints all units in this constraint set
public String get_units_as_string()
{
    int i = 0;
    StringBuilder sb = new StringBuilder();
    sb.append ("The following " + sorted_all_units().size() + " units are involved in compound constraints (in sorted order):\n");
    for (Unit u : sorted_all_units())
        sb.append ((++i) + ". " + u.short_toString() + "\n");
    return sb.toString();
}

// prints all constraints in this constraint set
public String get_constraints_as_string()
{
    int i = 0;
    StringBuilder sb = new StringBuilder();
    sb.append (_c.size() + " compound constraints in this constraint set\n");
    for (Constraint c : _c)
        sb.append ("Constraint " + (++i) + ". " + c + "\n");
    return sb.toString();
}

public void initialize()
{
    _formulas.clear();
    for (Constraint c : _c)
        c.set_is_formula(false);
}

public String short_toString()
{
    return "Compound constraint set: " + sorted_all_units().size() + " units, " + _c.size() + " constraints, " + _formulas.size() + " formulas";
}

public String toString()
{
    return get_units_as_string() + "\n" + get_constraints_as_string() + "\n" + get_formulas_as_string() + "\n";
}

// string representation of all formulas in this constraint set
public String get_formulas_as_string()
{
    StringBuilder sb = new StringBuilder();
    sb.append (_formulas.entrySet().size() + " formulas in this compound constraint set\n");
    int i = 0;
    for (Map.Entry<Unit,Constraint> e : _formulas.entrySet())
    {
        Unit u = e.getKey();
        Constraint c = e.getValue();
        Dimensions d = c.dimensions().solve_for (u);
        sb.append (++i + ". Formula for " + u.short_toString() + ": " + d.toString() + "\n");
    }
    return sb.toString();
}

}
