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

/*
TODO now:
	when storing a golden units model, clean the clones units out.
	when using a golden model, do not clone units that are in golden model.
*/

package unifi;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.Type;

import unifi.oo.MethodResolver;
import unifi.solver.Constraint;
import unifi.solver.ConstraintSet;
import unifi.solver.ConstraintSolver;
import unifi.solver.Fraction;
import unifi.units.FieldUnit;
import unifi.units.LocalVarUnit;
import unifi.units.MethodParamUnit;
import unifi.units.MultUnit;
import unifi.units.PhiUnit;
import unifi.units.ReturnValueUnit;
import unifi.units.Unit;
import unifi.util.BCELUtil;
import unifi.util.Util;

/** a collection of units... heh :-)
// a very important class in unifi
 */
public class UnitCollection implements Serializable {

public static final long serialVersionUID = 1L;

private static Logger logger = Logger.getLogger("unifi.UnitCollection");
private static boolean VERBOSE = (System.getProperty("unifi.verbose") != null);

private static final String prefixesToFilterGoldenUnits[] = {"sun.", "com.", "sunw."}; // for rt.jar

// all units and unif. events for this collection
private Set<Unit> allUnits = new LinkedHashSet<Unit>();

private Collection<UnificationEvent> allEvents = new LinkedHashSet<UnificationEvent>();
private ConstraintSet _cs = new ConstraintSet(); // linear equation constraint sets

// _reps.keySet() is the "unique units" for this collection
// value for the key is a list of all units whose rep this unit is.
// computed lazily, so compute_reps must have been
// called before _reps is accessed.
private Map<Unit,List<Unit>> _reps = new LinkedHashMap<Unit,List<Unit>>();
private boolean reps_setup_done = false;

// units_self_map is a map from each unit to itself.
// it is useful when looking up a unit from some other
// collection for its equivalent in this collection.
// (used by get_equiv_unit())
private HashMap<Unit, Unit> _units_self_map = new LinkedHashMap<Unit, Unit>();
public DisplayNames displayNames = new DisplayNames(); // names mapper for unit->user friendly names
private Stats stats; // stats about # of classes/methods etc analyzed.

public void add_event(UnificationEvent ue) {
	allEvents.add(ue);
}

public void set_constraint_set (ConstraintSet cset) { this._cs = cset; }
public ConstraintSet constraint_set () { return this._cs; }
public void set_stats (Stats s) { this.stats = s; }
public Stats get_stats () { return this.stats; }

public UnitCollection()
{
}

public void clearEvents()
{
	this.allEvents.clear();
	for (Unit u: allUnits)
		u.clearEvents();
}

public UnitCollection(Set<Unit> units, List<UnificationEvent> events)
{
    super();
    this.allUnits = units;
    this.allEvents = events;
    displayNames = new DisplayNames();
}

public void reset_constraints() { _cs = new ConstraintSet(); }

public void checkIds() {
	for (Unit u: allUnits)
		if (u.getId() == 0)
			logger.severe("unit " + u + " has 0 as its id");

	for (UnificationEvent e: allEvents)
		if (e.getId() == 0)
			logger.severe ("unifi-event " + e + " has 0 as its id");
}

/** returns the first unit whose toString begins with s */
public Unit findUnit(String s)
{
	for (Unit u: allUnits)
		if (u.toString().startsWith(s))
			return u;
	return null;
}

/** returns all units whose toString begins with s */
public List<Unit> findUnits(String s)
{
	List<Unit> list = new ArrayList<Unit>();
	for (Unit u: allUnits)
		if (u.toString().startsWith(s))
			list.add(u);
	return list;
}

public void assignUnitAndEventIds()
{
	int id = 1;
	for (Unit u: allUnits)
		u.setId(id++);

	id = 1;
	for (UnificationEvent e: allEvents)
		e.setId(id++);
}

/** cleans up this UC to keep only golden units */
public void makeGolden()
{
	markGoldenUnits();

	{
//		this block for compound constraints... we're not ready to enable yet, needs checking etc.
		// set up the compound constraints so they are written in terms of golden units, if possible
		Set<Unit> goldenUnits = new LinkedHashSet<Unit>();
		for (Unit u: allUnits)
			if (u.isGolden())
				goldenUnits.add(u);
		prepare_to_solve(goldenUnits);

		// throw away constraints not involving goldenUnits
		logger.info("Before deleting non-golden unit constraints: " + _cs.short_toString());
		_cs.delete_constraints_not_involving(goldenUnits);
		logger.info("After deleting non-golden unit constraints: " + _cs.short_toString());
		solve_constraints();
		// todo:fix formulas

		remove_non_golden();
	}

	// we just delete all constraints... remove this when the above is more stable
	reset_constraints();
}

/** returns true if s starts with ANY of the given prefixes */
private static boolean startsWith(String s, String[] prefixes)
{
	for (String prefix: prefixes)
		if (s.startsWith(prefix))
			return true;
	return false;
}

private static boolean filterOutUnit(Unit u)
{

	if ((u instanceof FieldUnit) && startsWith(((FieldUnit) u).full_name(), prefixesToFilterGoldenUnits))
		return true;
	/*
	if ((u instanceof FieldUnit) && !((FieldUnit) u).isInAnalyzedCode())
		return true;
	*/
	if ((u instanceof MethodParamUnit) && startsWith(((MethodParamUnit) u). get_full_sig(), prefixesToFilterGoldenUnits))
		return true;
	if ((u instanceof ReturnValueUnit) && startsWith(((ReturnValueUnit) u). get_full_sig(), prefixesToFilterGoldenUnits))
		return true;

	return false;
}

/* identifies golden units (param/rets and field) and marks then  as golden units in the current unit collection
 * and gives them golden ids
 */
public void markGoldenUnits()
{
	int goldenClustersCountBasic = 0; // not incl. dependent units

	// first find and mark all units that are golden
	// IMP: along with the obvious golden units, we also have to find their dependent units (length, arrayOf, elementOf etc)
	for (Unit rep: _reps.keySet()) {
		List<Unit> list = _reps.get(rep);
		List<Unit> goldenList = new ArrayList<Unit>();
		boolean fieldInList = false;
		for (Unit u: list)
		{
			if (filterOutUnit(u))
				continue;

			if (u instanceof FieldUnit) // just for seeing if ANY field is present, doesn't have to be non-private
				fieldInList = true;

			if (u instanceof FieldUnit && !((FieldUnit)u).is_private())
				goldenList.add(u);

			if (u instanceof MethodParamUnit && !((MethodParamUnit) u).isClone() &&
					((MethodParamUnit)u).isPublicOrProtected())
				goldenList.add(u);

			if (u instanceof ReturnValueUnit && !((ReturnValueUnit) u).isClone() &&
					((ReturnValueUnit)u).isPublicOrProtected())
				goldenList.add(u);
		}

		// if no fields in this cluster, remove method params/rv's since to be golden, they must be connected to a field
		if (!fieldInList)
			for (Iterator<Unit> it = goldenList.iterator(); it.hasNext(); )
			{
				Unit u = it.next();
				if (u instanceof MethodParamUnit || u instanceof ReturnValueUnit)
					it.remove();
			}

		for (Unit u: goldenList)
			markUnitAndDependentsAsGolden(u);

		if (goldenList.size() > 0)
			goldenClustersCountBasic++;
	}

	// now assign id's to every cluster that has at least one golden unit
	int goldenId = 0;
	for (Unit rep: _reps.keySet())
	{
		List<Unit> list = _reps.get(rep);
		boolean listHasGoldenUnit = false;
		for (Unit u: list)
		{
			if (u.isGolden())
			{
				listHasGoldenUnit = true;
				break;
			}
		}

		if (listHasGoldenUnit)
		{
			// assign id to all golden units in this list
			for (Unit u: list)
				if (u.isGolden())
					u.setGoldenId(goldenId);
			goldenId++;
		}
	}

	logger.info (goldenId + " classes of golden units marked (" + goldenClustersCountBasic + " basic)");
}


/** adds u and its dependent units to allUnits */
private static void markUnitAndDependentsAsGolden(Unit u)
{
	if (u == null)
		return;
	if (u.isGolden())
		return;

	u.markGolden();
	if (u.hasLengthUnit())
		markUnitAndDependentsAsGolden(u.getLengthUnit());
	Unit uElementOf = u.getElementOf();
	if (uElementOf != null)
		markUnitAndDependentsAsGolden(uElementOf);
	if (u.hasArrayOf())
		markUnitAndDependentsAsGolden(u.getArrayOf());
}

/** removes all units not marked as golden */
public void remove_non_golden()
{
	for (Iterator<Unit> it = allUnits.iterator(); it.hasNext(); ) {
		Unit u = it.next();
		if (!u.isGolden()) {
			it.remove();
			if (logger.isLoggable(Level.FINE))
				logger.fine ("Removing unit because it is not golden: " + u);
		}
	}

	// this is to pass verification check in compute_reps()
    for (Iterator<UnificationEvent>it= allEvents.iterator(); it.hasNext();) {
        UnificationEvent event = it.next();
        if (!allUnits.contains(event.get_unit_a()) ||
        		!allUnits.contains(event.get_unit_b())) {
        	it.remove();
        	event.get_unit_a().getUnificationEvents().remove(event);
        	event.get_unit_b().getUnificationEvents().remove(event);
        }
    }

    /* we should remove non golden fieldUnits/methodUnits here
     * since compute_reps() calls verify() which requires
     * all fieldUnits/methodUnits to be in uc.allUnits */
	MethodResolver.keepOnlyGolden();
	FieldUnit.keepOnlyGolden();

	compute_reps();

	allEvents.clear();
	for(Unit rep: _reps.keySet())
	{
		List<Unit> units = _reps.get(rep);
		if (units.size() == 1)
			continue;

		GoldenUnifiEvent e = new GoldenUnifiEvent(rep, units);
		for (Unit u: units) {
			u.getUnificationEvents().clear();
			u.addUnificationEvent(e);
		}
		allEvents.add(e);
	}
}

public void removeUnreachableUnits(Map<Unit, Collection<Unit>> rootUnits) {
	for (Unit rep: _reps.keySet())	{
		Collection<Unit> markedUnits = new LinkedHashSet<Unit>();
		Collection<Unit> unitsToMark = rootUnits.get(rep);
		Collection<Unit> nextUnitsToMark = new LinkedHashSet<Unit>();
		Collection<UnificationEvent> markedUnifiEvents = new LinkedHashSet<UnificationEvent>();

		while(nextUnitsToMark.size()>0) {
			markedUnits.addAll(unitsToMark);
			unitsToMark.addAll(nextUnitsToMark);
			nextUnitsToMark.clear();
			for (UnificationEvent e:UnificationEvent.select_events(unitsToMark)) {
				markedUnifiEvents.add(e);
				Unit a = e.get_unit_a();
				Unit b = e.get_unit_b();
				if (!markedUnits.contains(a)) { unitsToMark.add(a);}
				if (!markedUnits.contains(b)) { unitsToMark.add(b);}

			}
		}
	}
}

/** returns new UC with error clusters */
public UnitCollection checkGoldenUnits()
{
	System.out.println ("Checking golden units");
	List<Unit> diffReps = new ArrayList<Unit>();
	for (Unit rep: _reps.keySet())
	{
		List<Unit> list = _reps.get(rep);

		// maintain a count for how many units of each golden id we have seen (useful for reporting # of units in each cluster that got merged)
		Map<Unit, Integer> goldenUnitsToCount = new LinkedHashMap<Unit, Integer>();
		for (Unit u: list)
		{
			if (u.isGolden())
			{
				boolean found = false;
				// see if we've already seen this golden id
				for (Unit gu: goldenUnitsToCount.keySet())
				{
					if (gu.getGoldenId() == u.getGoldenId())
					{
						int count = goldenUnitsToCount.get(gu);
						count++;
						goldenUnitsToCount.put (gu, count);
						found = true;
						break;
					}
				}

				if (!found)
					goldenUnitsToCount.put(u, 1);
			}
		}

		if (goldenUnitsToCount.size() > 1)
		{
			logger.warning ("DIMENSION ERROR: the following " + goldenUnitsToCount.size() + " clusters got merged: ");
			for (Unit gu: goldenUnitsToCount.keySet())
				logger.warning ("  Golden Unit id: " + gu.getGoldenId() + ": " + getAllDisplayNames(gu) + "[and " + goldenUnitsToCount.get(gu) + " other unit(s)]");
			diffReps.add(rep);
		}
	}

	printPathBetweenDiffReps(diffReps);
	// all error_reps as a new unit collection
	return newUnitCollectionWithReps(diffReps);
}

public void printPathBetweenDiffReps(List<Unit> reps)
{
	StringBuilder sb = new StringBuilder();
	for (Unit rep: reps)
	{
		int i = 0;
		// create a map of golden id -> some unit for that golden id in this cluster
		Map<Integer, Unit> goldenIdToUnit = new LinkedHashMap<Integer, Unit>();
		List<Unit> cluster = _reps.get(rep);
		for (Unit u: cluster)
		{
			int goldenId = u.getGoldenId();
			if (goldenId >= 0 && goldenIdToUnit.get(goldenIdToUnit) == null)
				goldenIdToUnit.put(goldenId, u);
		}

		// now print a path between successive pairs of golden id units
		Unit prevGoldenUnit = null;
		for (Unit goldenUnit: goldenIdToUnit.values())
		{
			if (prevGoldenUnit != null)
			{
				sb.append ("----------\nPrinting path from golden unit id " + prevGoldenUnit.getGoldenId() + " to " + goldenUnit.getGoldenId() + "\n");

				List<PathInfo> path = find_path(goldenUnit, prevGoldenUnit);
				if (path != null)
				{
					for (PathInfo p: path)
					{
						Unit src = p.get_src();
						Unit dest = p.get_dest();
						UnificationEvent ue = p.get_event();
						sb.append ("src golden id = " + src.getGoldenId() + " dest golden id = " + dest.getGoldenId() + "\n");
						sb.append (i++ + "." + ue + "\n");
					}
				}
				else
					sb.append ("src golden id = " + prevGoldenUnit.getGoldenId() + " dest golden id = " + goldenUnit.getGoldenId() + " NO PATH\n");
			}
			prevGoldenUnit = goldenUnit;
		}
	}
	logger.warning (sb.toString());
}

/** creates a new UnitCollection with clusters represented by the units in reps */
public UnitCollection newUnitCollectionWithReps(List<Unit> reps)
{
	Set<Unit> newAllUnits = new LinkedHashSet<Unit>();
	Set<UnificationEvent> newAllEvents = new LinkedHashSet<UnificationEvent>();
	for (Unit rep: reps)
	{
		List<Unit> list = _reps.get(rep);
		newAllUnits.addAll(list);
		for (Unit u: list)
			newAllEvents.addAll(u.unifEvents);
	}
	UnitCollection newUC = new UnitCollection(newAllUnits, new ArrayList<UnificationEvent>(newAllEvents));
	newUC.compute_reps();
	return newUC;
}

public Collection<Unit> get_units() { return allUnits; }
public Collection<UnificationEvent> get_events() { return allEvents; }
public void add(Unit u)
{
	if (allUnits.contains(u))
		if (!(u instanceof MultUnit) && !u.isGolden()) // mult units exempted from warning for now
			logger.severe ("SEVERE Warning: Unit already exists: " + u);

	// if golden, unit already exists, no need to add to all Units
	if (!u.isGolden())
		allUnits.add(u);
}

public int size() { return allUnits.size(); }
public ConstraintSet get_mult_constraints() { return _cs; }

public void add_constraint(Unit u, Unit a, Unit b, Fraction coeff)
{
    _cs.add_constraint(u, a, b, coeff);
}

public boolean contains(Unit u) { return allUnits.contains(u); }

// returns a rep -> List<units represented by the rep> map
public Map<Unit,List<Unit>> get_reps()
{
    Util.ASSERT (reps_setup_done);
    return _reps;
}

/** space separated list of display names for this unit */
public String getAllDisplayNames(Unit u)
{
	// computes list of all names for this unit
	List<String> namesList = new ArrayList<String>();
	Unit rep = (Unit) u.find();
	List<Unit> allUnitsForThisRep = _reps.get(rep);
	// get display name for all units for this rep
	for (Unit u1: allUnitsForThisRep)
	{
		String dname = displayNames.getName(u1);
		if (!Util.nullOrEmpty(dname))
			namesList.add(dname);
	}

	// format namesList
	String displayName = "";
	if (namesList.size() > 1)
		displayName = "[";
	for (int i = 0; i < namesList.size(); i++)
	{
		if (i > 0)
			displayName += " ";
		displayName += namesList.get(i);
	}
	if (namesList.size() > 1)
		displayName += "]";

	if ("".equals (displayName))
		displayName = u.toString();
	return displayName;
}

/** drops events from allEvents which involve units not in allUnits.
 * recomputes a unit's unif. events
 */
private void recomputeAllEvents()
{
	// drop event unless both its ends are in allUnits
	for (Iterator<UnificationEvent> it = allEvents.iterator(); it.hasNext();)
	{
		UnificationEvent ue = it.next();
		if (!allUnits.contains(ue.get_unit_a()) || !allUnits.contains(ue.get_unit_b()))
			it.remove();
	}

	// clear all unif events in all units
	for (Unit u: allUnits)
	{
		Collection<UnificationEvent> c = u.unifEvents;
		if (c != null)
			c.clear();
	}

	// for all the events left in allEvents, add it to the unif. events of both ends of the event
	for (UnificationEvent e: allEvents)
	{
		Unit a = e.get_unit_a();
		Unit b = e.get_unit_b();
		a.addUnificationEvent(e);
		b.addUnificationEvent(e);
	}
}

/** removes all the phi units from this collection */
public void remove_phi_units()
{
	for (Iterator<Unit> it = allUnits.iterator(); it.hasNext(); )
	{
		Unit u = it.next();
		if (u instanceof PhiUnit)
			it.remove();
	}
	recomputeAllEvents();
}

/** returns whether or not t may be a primitive/string (or an array of primitive/string) */
private boolean maybePrimitiveOrString(Type t)
{
	// if type is null, usually means object type.
	// but could still be prim or string, so better play it safe
	if (t == null)
		return true;

	if (t instanceof ArrayType)
		t = ((ArrayType) t).getBasicType(); // for int[][], returns int

	return ((t instanceof BasicType) ||	t.getSignature().equals("Ljava/lang/String;"));
}

/** remove non-prim and non-string units from allUnits. does not remove arrays of prim/strings */
public void remove_non_prim_string_units()
{
	/*
	for (Iterator<Unit> it = allUnits.iterator(); it.hasNext(); )
	{
		Unit u = it.next();
		Type t = u.getType();

		if (!maybePrimitiveOrString(t))
		{
			it.remove();
			continue;
		}
	}
	*/

	for (Unit rep: _reps.keySet())
	{
		List<Unit> list = _reps.get(rep);
		boolean isPrimOrString = false;
		for (Unit u: list)
		{
			Type t = u.getType();
			if (t == null)
				continue;
			if (t instanceof ArrayType)
				t = ((ArrayType) t).getBasicType();
			if (t instanceof BasicType || BCELUtil.isStringType(t))
			{
				isPrimOrString = true;
				break;
			}
		}

		// if nothing associated with a prim or string in this cluster, remove all units in it
		if (!isPrimOrString)
			for (Unit u: list)
			{
				allUnits.remove(u);
				if (u instanceof FieldUnit) {
					FieldUnit fu = (FieldUnit)u;
					FieldUnit.globalFieldUnitDir.remove(fu.full_name());
				}
				if (u instanceof MethodParamUnit) {
					MethodParamUnit mpu = (MethodParamUnit)u;
					MethodUnits mu = MethodResolver.globalMethodUnitsDir.get(mpu.get_full_sig());
					mu.nullify_method_param_unit(mpu.get_index());
				}
				if (u instanceof ReturnValueUnit) {
					ReturnValueUnit rvu = (ReturnValueUnit)u;
					MethodUnits mu = MethodResolver.globalMethodUnitsDir.get(rvu.get_full_sig());
					mu.nullify_return_value_unit();
				}
				if (logger.isLoggable(Level.FINE))
					logger.fine ("Removing unit because it is not primitive or String: " + u);
			}
	}
	recomputeAllEvents();
    compute_reps();
}

private boolean isCloneUnit(Unit u)
{
	if ((u instanceof MethodParamUnit) && ((MethodParamUnit)u).isClone())
		return true;
	if ((u instanceof ReturnValueUnit) && ((ReturnValueUnit)u).isClone())
		return true;
	return false;
}

/* removes all clone units from this collection */
public void remove_clone_units()
{
	for (Iterator<Unit> it = allUnits.iterator(); it.hasNext(); )
	{
		Unit u = it.next();
		if (isCloneUnit(u))
			it.remove();
	}
//	recomputeAllEvents();
}

/** should be called after allUnits is known */
public void readDisplayNames(String filename) throws IOException
{
	displayNames.parseFile (filename);
	System.out.println ("Printing display names: " + displayNames);
}

/** set up the user friendly names for each unit by aggregating names
 * for all units in the same equivalence class */
private void setupDisplayNames()
{
	for (Unit rep : _reps.keySet())
	{
		Set<String> newNamesSet = new LinkedHashSet<String>();
		List<Unit> unitsForThisRep = _reps.get(rep);

		// collect all the names for all the units in this rep
		for (Unit u : unitsForThisRep)
		{
			Set<String> set = displayNames.getNames(u);
			if (set != null)
				newNamesSet.addAll(set);
		}

		// not maintainging display names in Unit any more
//		for (Unit u : unitsForThisRep)
//			u.setDisplayNames(newNamesSet);
    }
	System.out.println ("Display names = " + displayNames);
}

/** important function. sets up the reps list for the units in this uc */
public void compute_reps ()
{
    _reps.clear();
    _units_self_map.clear();
    for (Unit e : allUnits)
    {
        Unit rep = (Unit) e.find ();
        List<Unit> list = _reps.get (rep);
        if (list == null)
        {
            list = new ArrayList<Unit> ();
            _reps.put (rep, list);
            Util.ASSERT (_reps.get(rep) == list);
        }

        list.add (e);

        _units_self_map.put (e, e);
    }

    // now sort the rep sets so they will be displayed in a good order and merge all the attributes among all
    // units with the same rep
    for (Unit rep : _reps.keySet())
    {
    	List<Unit> list = _reps.get(rep);
        Collections.sort (list);

        for (Unit u : _reps.get(rep))
            rep.unitAttribs().merge(u.unitAttribs());
    }

    // now make sure the rep is the most stable element in the set for display
    Map<Unit,List<Unit>> new_reps = new LinkedHashMap<Unit,List<Unit>>();
    for (Unit rep : _reps.keySet())
    {
        List<Unit> l = _reps.get(rep);
        Unit best_rep = l.get(0);
        // brute force the reps to the best_rep for all elements in this class
        for (Unit x: l)
            x.set_class(best_rep);
        new_reps.put (best_rep, l);
    }
    Util.ASSERT (new_reps.size() == _reps.size());
    _reps = new_reps;

    setupDisplayNames();

    reps_setup_done = true;
    verify();
    System.out.println ("Units summary: " + allUnits.size() + " units in " + _reps.keySet().size() + " equivalence classes");
}

/** prepare types for units so that anything related to primitives or strings is marked correctly.
 * for units with null types, borrows a type from another unit in its cluster if possible.
 * for object types that have been unified with (array of)* string, sets the object type. */
public void patch_types()
{
	Type stringType = Type.getType("Ljava/lang/String;");

	for (Unit rep : _reps.keySet())
    {
		// string handling is a little tricky:
		// if u's type is any ref type, but its in the same cluster as a String type,
		// we have to set u's type to String
		// similarly for u's basic type (i.e. if u is an array of string, array of string, etc
		// we'll store in storedStringOrStringArrayType the type of the other
		// unit in this cluster that is of type string or stringarray

    	boolean isString = false;
    	Type storedStringOrStringArrayType = null;
    	// find a non-null type for this cluster
    	Type typeForThisCluster = null;
        List<Unit> list = _reps.get(rep);
        for (Unit u: list)
        {
        	Type uType = u.getType();
        	if (typeForThisCluster == null)
        		typeForThisCluster = uType;

        	Type uTypeBasic = uType;
        	if (uType != null)
        	{
	        	if (uTypeBasic instanceof ArrayType)
	        		uTypeBasic = ((ArrayType) uType).getBasicType();

	        	if (uTypeBasic != null && uTypeBasic.equals(stringType))
	        	{
	        		isString = true;
	        		storedStringOrStringArrayType = uType;
	        	}
        	}
        }

        // if there are units with null type, assign them the type typeForThisCluster
        if (typeForThisCluster != null)
        	for (Unit u: list)
        	{
        		if (u.getType() == null)
        			u.forceType(typeForThisCluster);
        	}

        /*
        // if any of the units in the cluster has type string, assign string to all of them
        // this happened running unifi on itself: unit X has object type, but is cast to a string.
        // unless we assign the type string to X, we'll lose the unit and any paths that run through it.
        // the above logic also carries over to (arrays of)+ strings

        /*
        if (isString)
        {
        	for (Unit u: list)
        	{
            	Type uType = u.getType();
            	Type uTypeBasic = uType;
            	if (uType != null)
            	{
    	        	if (uTypeBasic instanceof ArrayType)
    	        		uTypeBasic = ((ArrayType) uType).getBasicType();
            	}

            	if (!uTypeBasic.equals(stringType))
            	{
            		// we expect uType should be string or any of its superclasses or superinterfaces
//            		if (!uType.equals(objectType))
//            			Util.die("expecting to see string or object!");
            		u.forceType(storedStringOrStringArrayType);
            	}
        	}
        }
       */
    }
}

// gets a unit in this collection which is equivalent to u.
// u should NOT already be in this Unit collection
// if u is a method param or return val unit with method name like access$100,
// then by definition there is no equiv unit because these names are not
// stable across versions of code. revisit this if this function can ever be called
// in a situation where these method names *are* stable.
public Unit get_equiv_unit (Unit u)
{
    boolean u_is_param_or_retval =  (u instanceof MethodParamUnit) || (u instanceof ReturnValueUnit);
    if (u_is_param_or_retval)
    {
        String fullSig;
        if (u instanceof MethodParamUnit) { fullSig = ((MethodParamUnit) u).get_full_sig(); }
        else { fullSig = ((ReturnValueUnit) u).get_full_sig(); }

        // sig = A.B.C.m(...)
        int x = fullSig.indexOf("(");
        fullSig = fullSig.substring (0, x);
        // sig = A.B.C.m
        x = fullSig.lastIndexOf(".");
        fullSig = fullSig.substring (x+1);
        // sig = m
        if (fullSig.indexOf("access$") >= 0)
            return null;
    }

    Unit return_unit = _units_self_map.get(u);
    if (return_unit != null)
    {
        // return_units must be "equal to" u
        // return_unit must always be a different unit than u. we shouldn't be doing get_equiv_unit for
        // a unit which already existed in this collection
        Util.ASSERT (u.equals(return_unit));
        Util.ASSERT (return_unit != u);
    }
    return return_unit;
}

// returns all units associated in same eq class as u.
public List<Unit> select_units(Unit u)
{
    Unit rep = (Unit) u.find();
    List<Unit> c = _reps.get(rep);
    if (c == null)
    {
	System.out.println ("Unable to find rep for " + u);
	System.out.println ("Rep is " + rep);
	Util.ASSERT (false);
    }
    return c;
}

// returns all the unique representative units
public Collection<Unit> get_all_unique_units()
{
    return _reps.keySet();
}

//returns all the unique representative units
public List<Unit> get_all_unique_units_sorted_by_class_size()
{
	List<Unit> result = new ArrayList<Unit>();
	for (Unit u: _reps.keySet())
		result.add(u);

	Collections.sort (result, new Comparator() {
		public int compare(Object o1, Object o2)
		{
			Unit u1 = (Unit) o1;
			Unit u2 = (Unit) o2;
			return _reps.get(u2).size() - _reps.get(u1).size();
		}
	});
    return result;
}

public int get_num_units_for_rep (Unit u)
{
    return select_units(u).size();
}

private String sanitizeForXml(Unit u)
{
    String s = u.toString();
    s = "<![CDATA[" + s.replaceAll("]]>", "]]>]]><![CDATA[") + "]]>";
    s = s.replace((char)0xc, ' ');
    return s;
}

public void print_units_for_prefuse (String filename) throws IOException
{
	compute_reps(); // this seems to be required, otherwise it crashes. why ?? reps should already be up-to-date...
	verify();
    PrintWriter out = new PrintWriter (new FileOutputStream(filename));
    int i = 0;
    Map<Unit, Integer> countToUnitMap = new LinkedHashMap<Unit,Integer>();

    out.println (
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
"<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n" +
"  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
"  xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n" +
"  http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n" +

"  <key id=\"classname\" for=\"node\" attr.name=\"classname\" attr.type=\"string\"/>\n" +
"  <key id=\"type\" for=\"node\" attr.name=\"type\" attr.type=\"string\"/>\n" +
"  <key id=\"classname\" for=\"edge\" attr.name=\"classname\" attr.type=\"string\"/>\n" +
"  <key id=\"type\" for=\"edge\" attr.name=\"type\" attr.type=\"string\"/>\n");

    out.println ("<graph edgedefault=\"directed\">\n");

    // print all the nodes, except the ones which just have one node in its equiv class
    for (Unit u : allUnits)
    {
    	// suppress clone units and any singleton units
    	if (isCloneUnit(u))
    		continue;
    	if (_reps.get(u.find()).size() <= 1)
    		continue;

    	out.print ("<node id=\"" + i + "\">\n");
        out.print ("<data key=\"classname\">" + sanitizeForXml(u)  + "</data>\n");
        out.print ("<data key=\"type\">nonexec</data>\n");
        out.print ("</node>\n");
        countToUnitMap.put (u, i);
        i++;
    }

    // print edges (from each unit to its rep)  the nodes
    i = 0;
    for (Unit u : allUnits)
    {
    	// suppress clone units and any singleton units
    	if (isCloneUnit(u))
    		continue;

    	Unit rep = (Unit) u.find();
    	// if u is not a clone unit, its rep shouldn't be either.
   // 	Util.ASSERT (!isCloneUnit(rep)); // disabled asserts because now we are not deleting clone units
    	// if u is a singleton, ignore
    	if (_reps.get(rep).size() <= 1)
    		continue;

    	out.print ("<edge id=\"" + i + "\" source=\"" + countToUnitMap.get(u)
                + "\" target=\"" + countToUnitMap.get(rep) + "\">\n");
        out.print ("<data key=\"classname\">|EDGE|</data>\n");
        out.print ("<data key=\"type\">nonexec</data>\n");
        out.print ("</edge>\n");
        i++;
    }

    out.println ("</graph>\n</graphml>\n");
    out.close();
}

/** little helper class to represent one leg of a path */
public static class PathInfo {
    private Unit _src, _dest;
    private UnificationEvent _event;
    PathInfo(Unit src, Unit dest, UnificationEvent e) { _src = src; _dest = dest; _event = e; }
    Unit get_src() { return _src; }
    Unit get_dest() { return _dest; }
    public UnificationEvent get_event() { return _event; }
}

/** returns shortest path from src to target. based on BFS */
public List<PathInfo> find_path (Unit src, Unit target)
{
    Util.ASSERT(allUnits.contains(src));
    Util.ASSERT(allUnits.contains(target));
    Util.ASSERT (src.find() == target.find());

    // path to is a mapping of each unit -> pathinfo that reaches it first.
    Map<Unit, PathInfo> path_to = new LinkedHashMap<Unit, PathInfo>();

    // explore is list of nodes to explore in this step, next_explore is list of nodes in next step
    Collection<Unit> unitsToExplore = new LinkedHashSet<Unit>(), unitsToExploreNext = new LinkedHashSet<Unit>();
    unitsToExploreNext.add(src);

    while (unitsToExploreNext.size() != 0)
    {
        // System.out.println ("finished exploring, next round explore size is " + next_explore.size());
        unitsToExplore.clear();
        unitsToExplore.addAll(unitsToExploreNext);
        unitsToExploreNext.clear();

        for (Unit u : unitsToExplore)
        {
            Set<Unit> s = new LinkedHashSet<Unit>();
            s.add(u);
            Collection<UnificationEvent> events = UnificationEvent.select_events(s);


            for (UnificationEvent e : events)
            {
                Unit a = e.get_unit_a();
                Unit b = e.get_unit_b();

                // connections will track all the connections out from this node
                List<Unit> connections = new ArrayList<Unit>();

                // a and b don't mean much for golden events since they can cluster an arbitrary # of units
                if (!(e instanceof GoldenUnifiEvent))
                {
                	Util.ASSERT ((a == u) || (b == u));
	                Unit to = (a == u) ? b : a;
	                connections.add(to);
                }
                else
                	connections.addAll(((GoldenUnifiEvent) e).get_units());

                for (Unit x: connections)
                {
                	if (x == u)
                		continue; // connections could include u itself, so just ignore it

                	if (path_to.get(x) == null)
	                {
	                	// x is unvisited
	                    path_to.put (x, new PathInfo(u, x, e));
	                    unitsToExploreNext.add (x);
	                }

	                if (x == target)
	                {
	                	// found it, generate a path by walking path_to backwards
	                    List<PathInfo> full_path = new ArrayList<PathInfo>();
	                    Unit current = x;
	                    while (current != src)
	                    {
	                        PathInfo p = path_to.get(current);
	                        Util.ASSERT (p != null);
	                        full_path.add(p);
	                        current = p._src;
	                    }
	                    return full_path;
	                }
                }
            }
        }
    }

    return null;
}

public List<UnificationEvent> find_event_path (Unit src, Unit target)
{
    List<PathInfo> path = find_path(src, target);
    List<UnificationEvent> result = new ArrayList<UnificationEvent>();
    for (PathInfo p : path)
        result.add (p.get_event());
    return result;
}


/*
private Multigraph generateGraphForJGraphT(Unit srcUnit) {
	Multigraph<UnitNode, UnifiEdge> g =
        new Multigraph<UnitNode, UnifiEdge>(UnifiEdge.class);
           // new ClassBasedEdgeFactory<UnitNode, UnifiEdge>(UnifiEdge.class));

	Collection<Unit> explore = new ArrayList<Unit>(), next_explore = new ArrayList<Unit>();
	next_explore.add(srcUnit);
	Map<Unit, UnitNode> unitToNode = new HashMap<Unit, UnitNode>();
	while(next_explore.size()!=0) {
		explore.clear();
		explore.addAll(next_explore);
		next_explore.clear();

		for (Unit u:explore) {
            Set<Unit> s = new LinkedHashSet<Unit>();
            s.add(u);
            Collection<UnificationEvent> events = UnificationEvent.select_events(s);

            for (UnificationEvent e:events) {
            	Unit a=e.get_unit_a();
            	Unit b=e.get_unit_b();

            	UnitNode n1 = unitToNode.get(a);
            	if (n1==null) {
            		n1 = new UnitNode(a);
            		unitToNode.put(a, n1);
            	}
            	UnitNode n2 = unitToNode.get(b);
            	if (n2==null) {
            		n2 = new UnitNode(b);
            		unitToNode.put(b, n2);
            	}
            	boolean added=false;
            	added = g.addVertex(n1);
            	if (added) next_explore.add(a);
            	added = g.addVertex(n2);
            	if (added) next_explore.add(b);
            	g.addEdge(n1, n2, new UnifiEdge(e));
            }
		}
	}
    return g;
}*/
/*
private void contract_edge(Multigraph<UnitNode, UnifiEdge> g, UnifiEdge chosenEdge) {
	UnifiEdge e1 = (UnifiEdge)chosenEdge;
	UnitNode u1 = g.getEdgeSource(e1);
	UnitNode u2 = g.getEdgeTarget(e1);

	Set<UnifiEdge> edgesOfU1, edgesOfU2;
	edgesOfU1 = g.edgesOf(u1);
	edgesOfU2 = g.edgesOf(u2);

	assert(g.vertexSet().contains(u1));
	assert(g.vertexSet().contains(u2));

//	System.out.println("ENTER contract_edge: edge size:" + g.edgeSet().size());
//	if (edgesOfU1.size() >= edgesOfU2.size()) {
		really_contract_edge(g, u1, u2, edgesOfU2);
	//} else {
//		really_contract_edge(g, u2, u1, edgesOfU1);
	//}
//	System.out.println("EXIT contract_edge: edge size:" + g.edgeSet().size());
}*/
/*
private UnitNode clone_vertex_in_graph(Multigraph<UnitNode, UnifiEdge> g, UnitNode u) {
	Set<UnifiEdge> edges = g.edgesOf(u);

	UnitNode cloned = u.clone();
	g.addVertex(cloned);

	for(UnifiEdge e:edges) {
		UnitNode u1, u2, o;
		u1 = g.getEdgeSource(e);
		u2 = g.getEdgeTarget(e);

		o = u.equals(u1) ? u2:u1;
		g.addEdge(cloned, o, e.clone());
	}

	Set<UnifiEdge> cloned_edges = new LinkedHashSet<UnifiEdge>();
	cloned_edges.addAll(edges);
	g.removeAllEdges(cloned_edges);
	g.removeVertex(u);

	return cloned;
}*/
/*
private void really_contract_edge(
		Multigraph<UnitNode, UnifiEdge> g, UnitNode u1,
		UnitNode u2, Set<UnifiEdge> edgesOfU2) {

	Set<UnifiEdge> clonedEdgesOfU2 = new LinkedHashSet<UnifiEdge>();
	clonedEdgesOfU2.addAll(edgesOfU2);
	for(UnifiEdge edge:clonedEdgesOfU2) {
		UnitNode a = g.getEdgeSource(edge), b = g.getEdgeTarget(edge);
		UnitNode other = a.equals(u2) ? b:a;

		Util.ASSERT(!other.equals(u2));
		if (other.equals(u1)) continue; // the edge between the merged nodes disappears
		else {
			g.addEdge(u1, other, new UnifiEdge(edge.e));
		}

		g.removeEdge(edge);
	}
	g.removeEdge(u1, u2);
	g.removeVertex(u2);
}
private Multigraph<UnitNode, UnifiEdge>
contract_for_min_cut(Multigraph<UnitNode, UnifiEdge> g, int sizelimit) {
	Multigraph<UnitNode, UnifiEdge> g2 = (Multigraph<UnitNode, UnifiEdge>) g.clone();

	Random r = new Random();
	int size=g2.vertexSet().size();
	Util.ASSERT(size == g.vertexSet().size());
	int prev_size = size;
	while(size > sizelimit) {
//		ConnectivityInspector<UnitNode, UnifiEdge> ci = new ConnectivityInspector<UnitNode, UnifiEdge>(g2);
		//Util.ASSERT(ci.isGraphConnected());

		Object e[] = g2.edgeSet().toArray();
		Util.ASSERT(e.length > 0);
		int idx = r.nextInt(e.length);

		UnifiEdge chosenEdge = (UnifiEdge)e[idx];
		contract_edge(g2, chosenEdge);
		prev_size = size;
		size = g2.vertexSet().size();
		Util.ASSERT(prev_size == size +1);
		Util.ASSERT(g2.edgeSet().size() > 0);
		Util.ASSERT(g2.vertexSet().size() < g.vertexSet().size());

		//Util.ASSERT(ci.isGraphConnected());
	}
	return g2;
}*/

/*
private Set<UnifiEdge> brute_force_min_cut(Multigraph<UnitNode, UnifiEdge> g) {
	Object[] edges = g.edgeSet().toArray();
	int vertices = g.vertexSet().size();
	int all_possible_edges = (vertices -1) * vertices / 2;

	if (g.edgeSet().size() > 8 && g.edgeSet().size() >= all_possible_edges * 0.8) {
		System.out.println("giving up on this graph");
		throw new MincutException("giving up on this graph");
	}
	PermutationGenerator x = new PermutationGenerator(edges.length);
	Set<UnifiEdge> mincut = g.edgeSet();
	int mincut_size = mincut.size();
	while (x.hasMore()) {
		Multigraph<UnitNode, UnifiEdge> cloned =(Multigraph<UnitNode, UnifiEdge>)g.clone();
		//ConnectivityInspector ci = new ConnectivityInspector<UnitNode, UnifiEdge>(cloned);
		int[] indices = x.getNext();
		Set<UnifiEdge> removedEdges = new LinkedHashSet();
		for (int i=0; i<indices.length && i < mincut_size; i++) {
			removedEdges.add((UnifiEdge)edges[i]);
			UnitNode a, b;
			a=cloned.getEdgeSource((UnifiEdge)edges[i]);
			b=cloned.getEdgeTarget((UnifiEdge)edges[i]);

			cloned.removeEdge((UnifiEdge)edges[i]);
			if (cloned.getEdge(a, b)!=null) { // connected!
				continue;
			} else if (DijkstraShortestPath.findPathBetween(cloned, a, b) == null) {
				Util.ASSERT (removedEdges.size() <= mincut_size);
				mincut = removedEdges;
				mincut_size = removedEdges.size();
				break;
			}
		}
		if (mincut_size <= 1) break;
	}
	return mincut;
}*/
/*
private Set<UnifiEdge> brute_force_min_cut(Multigraph<UnitNode, UnifiEdge> g) {
	Object[] edges = g.edgeSet().toArray();
	int vertices = g.vertexSet().size();
	int all_possible_edges = (vertices -1) * vertices / 2;

	for (int i=0; i<edges.length-1; i++) {
		if (i>=6) { throw new MincutException("giving up on this graph"); }
		CombinationGenerator x = new CombinationGenerator(edges.length, i+1);
		int[] indices;
		while(x.hasMore()) {
			indices = x.getNext();
			Multigraph<UnitNode, UnifiEdge> cloned = (Multigraph<UnitNode, UnifiEdge>) g.clone();
			ConnectivityInspector ci = new ConnectivityInspector<UnitNode, UnifiEdge>(cloned);
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
}*/
/*
private Set<UnifiEdge> fast_cut(Multigraph<UnitNode, UnifiEdge> g) {
	int graph_size = g.vertexSet().size();
	if (graph_size <= 6) {
		return brute_force_min_cut(g);
	}

	Multigraph<UnitNode, UnifiEdge> g1, g2;
	int size_limit = (int)(((double)graph_size)/Math.sqrt(2));

	g1 = contract_for_min_cut(g, size_limit);
	g2 = contract_for_min_cut(g, size_limit);

	Set<UnifiEdge> cut1=null, cut2=null;
	try {
		cut1 = fast_cut(g1);
		cut2 = fast_cut(g2);
	} catch (MincutException e) {
		if (cut1==null) {
			return fast_cut(g2);
		} else {
			return cut1;
		}
	}
	if (cut1.size() < cut2.size())
		return cut1;
	else
		return cut2;

//	int mincut1=0, mincut2=0;
//	for (UnifiEdge e:cut1)
		//mincut1 += ((UnifiEdge)e).size();
	//for (UnifiEdge e:cut2)
//		mincut2 += ((UnifiEdge)e).size();
//
//	if (mincut1 < mincut2) return cut1;
//	else return cut2;
}*/
/*public Map<Unit, Set<UnifiEdge>> find_weak_link() {
	Map<Unit, Set<UnifiEdge>> weak_link_map = new HashMap<Unit, Set<UnifiEdge>>();
	System.out.println("\n+--------------------------------------+");
	System.out.println("Finding weak links between units");
	for (Unit u:get_all_unique_units_sorted_by_class_size()) {
		if (_reps.get(u).size() < 100) break;

		System.out.println(" Computing weak links for unit:"+u + " size:"+_reps.get(u).size());

		Multigraph<UnitNode, UnifiEdge> g = generateGraphForJGraphT(u);
		try {
			Set<UnifiEdge> weak_link = fast_cut(g);
			weak_link_map.put(u, weak_link);

			System.out.println("Unit "+u+" has weak link of size:"+weak_link.size());
			for (UnifiEdge e:weak_link) {
				System.out.println("  " +weak_link);
			}
		} catch (MincutException e) {
			System.out.println("giving up on unit:" + u);
			continue;
		}
	}

	return weak_link_map;
}*/

public String get_path_str(Unit src, Unit target)
{
    StringBuilder sb = new StringBuilder();

    sb.append ("looking for path from " + src + " to " + target + "\n");
    List<PathInfo> path = find_path(src, target);
    if (path == null)
        sb.append ("No path exists\n");
    else
    {
        int count = 0;
        for (PathInfo p : path)
            sb.append (++count + ". " + p.get_event()+"\n");
    }
    return sb.toString();
}

public void print_path(Unit src, Unit target)
{
    System.out.println (get_path_str (src, target));
}

public List<PathInfo> find_path (int src, int target)
{
    return find_path (get_unit_number(src), get_unit_number(target));
}

private Unit get_unit_number(int x)
{
    int i = 0;
    for (Unit u : allUnits) { if (++i == x)  { return u; } }
    Util.die ();
    return null;
}

public void print_path(int src, int target)
{
    Unit src_unit = get_unit_number(src), target_unit = get_unit_number(target);

    Util.ASSERT (src_unit != null);
    Util.ASSERT (target_unit != null);

    print_path (src_unit, target_unit);
}

private boolean consider_unit (Unit u, boolean only_prims)
{
    if (u == null)
        return false;
    if (u instanceof LocalVarUnit) // don't always know local var type, give benefit of doubt
        return true;

    Type t = u.getType();

    // if type is null, usually means object type
    if (only_prims)
    {
        if (t == null)
            return false;
        // consider only strings and basic types if only_prims is defined
        if ((t instanceof BasicType) ||
             t.getSignature().equals("Ljava/lang/String;"))
        {
            return true;
        }
        else if (t instanceof ArrayType)
        {
            return consider_unit (u.getArrayOf(), only_prims);
        }
    }

    return true;
}

/** note: remove_phi_units is expected to have been called before calling this. */
public void print_units ()
{
    System.out.println ("DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");
    System.out.println ("Printing dimensionless units");
    int count = 1;
    for (Unit u : allUnits)
    {
        if (u.isDimensionLess())
            System.out.println (count++ + ". " + u.short_toString());
    }
    System.out.println ("DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

    boolean only_prims = (System.getProperty("unifi.track.references") == null);

    HashMap<Integer, Integer> histogram = new LinkedHashMap<Integer, Integer>();

    // print units with more than one element first
    System.out.println ("Printing " + _reps.keySet().size() + " clusters with more than 1 element");
    count = 0;
    List<Unit> orderedUnits = get_all_unique_units_sorted_by_class_size();
    for (Unit u : orderedUnits)
    {
        if (!consider_unit(u, only_prims))
            continue;

        // no phi units should exist at this point
	    List<Unit> list = _reps.get (u);
        //Util.ASSERT (!( u instanceof PhiUnit));
        for (Iterator<Unit> it = list.iterator(); it.hasNext(); )
        {
            Unit u1 = it.next();
            //Util.ASSERT (!(u1 instanceof PhiUnit));
        }

        int size = list.size();

        if (size <= 1)
            continue;

        if (histogram.get(size) != null)
            histogram.put(size, histogram.get(size) + 1);
        else
            histogram.put(size, 1);

        count++;
        System.out.println ("***************************************************************");
        Collections.sort (list);

        Unit rep = list.get(0);
        String displayName = getAllDisplayNames(rep);

        if (Util.nullOrEmpty(displayName))
        	System.out.println (count + ". The following " + size + " have the same unit:");
        else
        	System.out.println (count + ". The following " + size + " have the unit: " + displayName);

        // suppress printing of clone units, their numbers tend to swamp out everything else
        // just print a summary of their count
        int rvCloneCount = 0;
        int paramCloneCount = 0;
        int mergeCount = 0;
        for (Iterator<Unit> list_it = list.iterator(); list_it.hasNext (); )
        {
            Unit u1 = list_it.next();
            boolean isParamClone = (u1 instanceof MethodParamUnit && ((MethodParamUnit) u1).isClone());
            boolean isRVClone =	(u1 instanceof ReturnValueUnit && ((ReturnValueUnit) u1).isClone());
            boolean isMerge = (u1 instanceof PhiUnit);
            if (isParamClone)
            	paramCloneCount++;
            if (isRVClone)
            	rvCloneCount++;
            if (isMerge)
            	mergeCount++;

            if (VERBOSE || (!isParamClone && !isRVClone && !isMerge))
            	System.out.println ("  " + u1 + "[" + u1.getUnificationEvents().size() + " unify events]");
        }

        if (!VERBOSE)
        {
        	if (paramCloneCount > 0 || rvCloneCount > 0 || mergeCount > 0)
        		System.out.println("  Not shown: "
        							+ ((paramCloneCount > 0) ? paramCloneCount + " method param clone(s) " : "")
        							+ ((rvCloneCount > 0) ? rvCloneCount + " return value clone(s) " : "")
        							+ ((mergeCount > 0) ? mergeCount + " merge unit(s)" : ""));
        }

        System.out.println ("---------------------------------------------------------------");

        Collection<Unit> units = new LinkedHashSet<Unit>();
        units.addAll(list);
        Collection<UnificationEvent> events = UnificationEvent.select_events (units);
        List<UnificationEvent> event_list = new ArrayList<UnificationEvent>();
        event_list.addAll (events);
        Collections.sort (event_list);

        System.out.println ("The following are the " + events.size() + " unification events for this unit:");
        int count1 = 0;
        for (UnificationEvent ue : event_list)
        {
            count1++;
            System.out.println (count + "." + count1 + ". " + ue);
            // if > 50 events, we truncate at 10
            if (!VERBOSE && count1 >= 10 && event_list.size() > 50)
            {
                System.out.println ("... and " + (event_list.size()-count1) + " others");
                break;
            }
        }
    }

    // print units with only one element
    System.out.println ("PPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPP");
    System.out.println ("Printing units with only one element");
    count = 0;
    for (Unit u : _reps.keySet())
    {
        if (!consider_unit(u, only_prims))
            continue;
        List<Unit> list = _reps.get (u);
        Util.ASSERT (list.size() > 0);
        if (list.size() > 1)
            continue;
        count++;

        String displayName = getAllDisplayNames(u);
        if (displayName != null)
        	System.out.println (count + ". " + displayName);
        else
        	System.out.println (count + ". " + u);
    }

    // add count of units sets of size 1
    histogram.put (1, count);

    System.out.println ("there were a total of " + allUnits.size() +
                        " units, in " + _reps.size() + " classes (" +
                        count + " individual), with " +
                        allEvents.size() + " events\n" +
                        "(this includes all units, not only primitives)");

    // misusing Map.Entry here as a 2 element tuple
    List<Map.Entry<Integer,Integer>> l = new ArrayList<Map.Entry<Integer,Integer>>(histogram.size());
    l.addAll (histogram.entrySet());

    Collections.sort (l,
                      new Comparator<Map.Entry<Integer, Integer>>() {
                           public int compare (Map.Entry<Integer,Integer> o1, Map.Entry<Integer, Integer> o2) {
                           return (o2.getKey() - o1.getKey());
                         }});
    System.out.println ("EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE");
    System.out.println ("Histogram of unit set sizes");
    for (Map.Entry<Integer,Integer> e : l)
        System.out.println ("Size " + e.getKey() + ": " + e.getValue() + " unit set(s)");

    // print out unit type histogram
    Map<Class, Integer> m = new LinkedHashMap<Class, Integer>();
    for (Unit u : allUnits)
    {
        Class<? extends Unit> c = u.getClass();
        Integer i = m.get(c);
        if (i == null)
            m.put (c, Integer.valueOf(1));
        else
            m.put (c, Integer.valueOf(i.intValue() + 1));
    }

    System.out.println ("________________________________________");
    System.out.println ("Histogram of unit types");
    for (Map.Entry<Class,Integer> e : m.entrySet())
        System.out.println (e.getKey() + ": " + e.getValue());

    System.out.println ("# units = " + allUnits.size());
    System.out.println ("# events = " + allEvents.size());
    System.out.println ("Analysis stats:\n" + stats.toString());
}

/** need to report:
total # of classes
total # of interfaces
total # of method bodies analyzed in classes specified.
total # of method bodies not analyzed in classes specified (due to hashcode, compareTo, etc).
unique # of methods w.r.t munit mapping
total # of params/retvals
total # of primitive params/retval
total # of String params/retval
total # of non-polymorphic primitive params/retval
total # of non-polymorphic String params/retval

# of unification points per unit, excluding for 1-unit buckets.
*/

public void print_in_analyzed_code_histogram()
{
    // print out unit type histogram
    Map<Class, Integer> m = new LinkedHashMap<Class, Integer>();
    for (Unit u : allUnits)
    {
        Class<? extends Unit> c = u.getClass();
        Integer i = m.get(c);
        if (i == null)
            m.put (c, Integer.valueOf(1));
        else
            m.put (c, Integer.valueOf(i.intValue() + 1));
    }

    System.out.println ("________________________________________");
    System.out.println ("Histogram of unit types");
    for (Map.Entry<Class,Integer> e : m.entrySet())
        System.out.println (e.getKey() + ": " + e.getValue());

}


/** recomputes units order and updates _cs accordingly.
 * stable_units is the preferred set of units ordered
 * before others and may be null.
 */
public void prepare_to_solve (Set<Unit> stable_units)
{
//    compute_reps(); reps should already have been computed
    // compute_preferred_rep_map(stable_units);
    // prepare ConstraintSet to solve
	for (Iterator it = _cs.constraints().iterator(); it.hasNext(); )
		((Constraint) it.next()).verify();
    _cs.prepare_to_solve (_reps, stable_units);
	for (Iterator it = _cs.constraints().iterator(); it.hasNext(); )
		((Constraint) it.next()).verify();
}

public void solve_constraints()
{
    // clear constraint set and formulas here.
    _cs.initialize();
    compute_reps();
    verify();
    new ConstraintSolver().solve_constraints(this);
}

// to be called only after compute_reps
public void verify()
{
    Util.ASSERT (reps_setup_done);

    // ensure all units in each rep's list actually exist in the collection
    for (Map.Entry<Unit,List<Unit>> me : _reps.entrySet())
    {
        List<Unit> list = me.getValue();
        for (Unit u1 : list)
        {
            Util.ASSERT (allUnits.contains(u1));
        //    util.ASSERT (u1.find() == rep);
        }
    }

    // make sure every unit and event equals itself
    for (Unit u : allUnits)
        Util.ASSERT (u.equals(u));
    for (UnificationEvent e : allEvents)
        Util.ASSERT (e.equals(e));

    // ensure all units mentioned in unif events actually exist in the collection
    for (Object o : allEvents)
    {
        UnificationEvent event = (UnificationEvent) o;
        Util.ASSERT (allUnits.contains(event.get_unit_a()));
        Util.ASSERT (allUnits.contains(event.get_unit_b()));
    }

    // ensure all unit's unif events exist in allEvents
    for (Unit u: allUnits)
    {
    	Collection<UnificationEvent> c = u.getUnificationEvents();
    	if (c != null)
    		for (UnificationEvent e: c)
    		{
    			if (!allEvents.contains(e))
    			{
    				//Util.die("The following event is not in allEvents:\nunit: " + u + "\nevent: " + e);
    				System.out.println("The following event is not in allEvents:\nunit: " + u + "\nevent: " + e);
    			}
    		}
    }

    // ensure there are no duplicate units by adding each element to set s,
    // but first ensuring the element doesn't already exist.
    // make sure element exists in the set after adding it
    Set<Unit> s = new LinkedHashSet<Unit>();
    for (Unit u : allUnits)
    {
        Util.ASSERT (!s.contains(u));
        s.add(u);
        Util.ASSERT (s.contains(u));
    }

    // self map must always map u1 -> u1
    for (Map.Entry<Unit, Unit> me : _units_self_map.entrySet())
        Util.ASSERT (me.getKey() == me.getValue());

    // these verify's fail for diff UCs, so we run them only if this equals the "global" UC
    /*
    if (this == Unit._current_unit_collection)
    {
		FieldUnit.verifyAllUnitsInUC(this);
	    MethodResolver.verifyAllUnitsInUC(this);
    }
    */
    // verify compound constraints
    _cs.verify();
}

// in addition to verify, makes sure that path exists between every pair of units in the same equiv class
/* public void full_verify()
{
    verify();
    for (Unit rep : _reps.keySet())
    {
        List<Unit> list = _reps.get(rep);
        for (int i = 1; i < list.size(); i++)
        {
        	Unit u = list.get(i-1);
        	Unit u1 = list.get(i);
        	util.ASSERT(u.equals(u));
        	util.ASSERT(u1.equals(u1));

        	if (u.equals(u1))
        			System.out.println ("SEVERE WARNING: repeated unit: " + list.get(i-1)); // i saw this happen once -sgh

            List<PathInfo> p = find_path (u, u1);
            if (p == null)
                System.out.println ("SEVERE Warning (cluster size " + list.size() + "): no path between \n  " + u.toFullString() + "     and\n  " + u1.toFullString());
        }
    }
} */
public void full_verify()
{
	verify();
	System.out.println("\n\nFully verifying unit/unifievents.");
    for (Unit rep : _reps.keySet()) {
    	Unit srcUnit = rep;

    	Collection<Unit> explore = new ArrayList<Unit>(), next_explore = new ArrayList<Unit>();
    	next_explore.add(srcUnit);
    	Set<Unit> remainingUnits = new LinkedHashSet<Unit>(_reps.get(srcUnit));
    	remainingUnits.remove(srcUnit);
		Set<Unit> covered = new LinkedHashSet<Unit>();

		while (next_explore.size() != 0) {
			explore.clear();
			explore.addAll(next_explore);
			next_explore.clear();

			for (Unit u : explore) {

				if (logger.isLoggable(Level.FINE))
					logger.fine ("Exploring node : " + u);

				Set<Unit> s = new LinkedHashSet<Unit>();
				s.add(u);
				Collection<UnificationEvent> events = UnificationEvent.select_events(s);

				if (logger.isLoggable(Level.FINE))
					logger.fine (events.size() + " events");

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

					for (Unit ru:relatedUnits) {
						boolean added = covered.add(ru);
						if (added)
							next_explore.add(ru);
					}
				}
			}
		}
		if (remainingUnits.size() != 0) {
			System.out.println(remainingUnits.size()+" disconnected units for rep: " + srcUnit + " [" +
							   srcUnit.getUnificationEvents().size() + " events]");
			for (Unit x:remainingUnits) {
				System.out.println("  " + x + " [" + x.getUnificationEvents().size() + " events]");
			}
		}
	}
}

public String toString()
{
	return allUnits.size() + " units, " + allEvents.size() + " events, " + _cs.constraints().size() + " compound constraints";
}

/** debug method to return list of units whose toString contains the given string */
public List<Unit> lookup(String s)
{
	List<Unit> result = new ArrayList<Unit>();
	for (Unit u: allUnits)
		if (u.toString().indexOf(s) >= 0)
			result.add(u);
	return result;
}

public static void main (String args[]) throws Exception
{
	String filename = args[0];
	// read from a file
	ObjectInputStream ois;
	try {
		ois = new ObjectInputStream(new FileInputStream(filename));
	} catch (Exception e) {
		System.err.println("Warning: error opening file: " + filename);
		return;
	}

	UnitCollection uc = (UnitCollection) ois.readObject();
	uc.checkIds();
	uc.full_verify();
}

public void assign_seaview_ids()
{
	int rep_id = 0, id = 0;
	for (Unit u: _reps.keySet())
	{
		u.seaview_rep_id = rep_id++;
		for (Unit u1: _reps.get(u)) // ideally assign id's only for clusters connected to a field
		{
			u1.seaview_rep_id = u.seaview_rep_id;
			u1.seaview_id = id++;
		}
	}
}

}
