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

package unifi.contextsensitive;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import unifi.MethodUnits;
import unifi.solver.Fraction;
import unifi.units.AllocUnit;
import unifi.units.ArrayLengthUnit;
import unifi.units.FieldUnit;
import unifi.units.MethodParamUnit;
import unifi.units.MultUnit;
import unifi.units.ReturnValueUnit;
import unifi.units.Unit;
import unifi.util.Util;

/** a method summary for a mapped method.
 * there are 2 kinds of method summaries - a master method summary and cloned summaries.
 * there is one "master method summary" object for a single method interface.
 * if there are multiple methods that implement that method interface, all
 * those methods update the same master method summary.
 * each invoke site for the method interface has a clone of this summary.
 * A cloned method summary does not have unification events.
 * It has a pointer to a MethodUnits which contains the unification events.
 */
public class MethodSummary implements Serializable {

public static Logger parent_logger = Logger.getLogger("unifi.contextsensitive");
private static Logger logger = Logger.getLogger("unifi.contextsensitive.MethodSummary");
static { logger.setParent(parent_logger); parent_logger.setParent(Logger.getLogger("unifi"));  }

/* information about the method for which this object is a summary (both master and clones) */
private MethodUnits methodUnits; /* method units object this method maps to */
private MethodParamUnit[] params;
private ReturnValueUnit retVal;
private boolean isClone; // is this a clone or a master method summary ?

// data for master method summaries only
private List<MethodSummary> clones;
private Set<Unit> allUnits; // all units in the summary. for MMS only. does not literally include all units, but only units reachable from params/retval
private Map<Unit,List<Unit>> repToUnitsMap;

// data for clones only
private Map<Unit,Unit> cloneMap = null; /* mapping of units in the MMS to units in this summary */
private MethodInvoke methodInvoke; // method invoke point for this clone (null for MMS)
private int nUpdates = 0; // # of times update has been called on this clone

private List<MultUnit> multUnits; /** mult units in this method */

/** this constructor called with null params and rvu if its a clone */
public MethodSummary (MethodUnits mu, MethodParamUnit[] params, ReturnValueUnit rvu)
{
	this.methodUnits = mu;
	// set initial params and retval to the method units' params and retval.
	this.params = params;
	this.retVal = rvu;
	// currently, an easy way to differentiate a clone from a master method summary
	// is if params and retval are null, because for a clone these are set in cloneSummary()
	// this may change in future.
	if (params == null && retVal == null)
		isClone = true;
	if (!isClone)
		clones = new ArrayList<MethodSummary>();

	multUnits = new ArrayList<MultUnit>();
}

public ReturnValueUnit getReturnValueUnit() { return retVal; }
public MethodParamUnit[] getParamUnits() { return params; }

public void addMultUnit (MultUnit mu)
{
	// shouldn't contain mu already
	Util.ASSERT (!multUnits.contains(mu));
	multUnits.add(mu);
}

/** clone a unit including its depending units like arrayOf, elementOf and lengthUnit (but not including mult units)
 * the new units are updated in cloneMap.
 * this must be a clone summary, u must belong to the master summary.
 * returns true or false depending on whether a new unit was created */
private boolean cloneUnit(Unit u, int cloneNum, boolean registerUnit)
{
	Util.ASSERT (this.isClone);
	Util.ASSERT (cloneNum >= 0);
	boolean unitCreated = false;
	if (u == null)
		return unitCreated;

	Unit uClone = cloneMap.get(u);
	if (uClone == null)
	{
		if (u.isGolden())
		{
			cloneMap.put(u, u);
			return false;
		}

		unitCreated = true;
		uClone = (Unit) u.clone();
		uClone.reset(); // reset UFO information

		// note: cloneNum must be set before calling register_unit because it uses hashcode
		if (uClone instanceof MethodParamUnit)
			((MethodParamUnit) uClone).setCloneNum(cloneNum);
		if (uClone instanceof ReturnValueUnit)
			((ReturnValueUnit) uClone).setCloneNum(cloneNum);

		if (registerUnit)
			Unit.registerUnit(uClone);
		cloneMap.put(u, uClone);
		Util.ASSERT (cloneMap.get(u) == uClone);
	}

	// does the clone need an associated lenght unit ? create it if so
	// hasLengthUnit() should be used instead of get_length_ue == null,
	// because calling get_length_ue automatically allocates the length ue
	if (u.hasLengthUnit() && !uClone.hasLengthUnit())
	{
		Unit uLength = u.getLengthUnit();
		boolean unitCreatedForLength = cloneUnit(uLength, cloneNum, false); // don't register, we need to set up lengthof etc for correct hashcode
		unitCreated |= unitCreatedForLength;
		ArrayLengthUnit uLengthClone = (ArrayLengthUnit) cloneMap.get(uLength);
		uClone.setLengthUnit(uLengthClone);
		// register it only if unit has been newly created
		// register it only after the object has been completely constructed, otherwise the hashcode changes
		if (unitCreatedForLength)
			Unit.registerUnit(uLengthClone);
	}
/*	Not sure this needs to be commented out.
	Unit uElementOf = u.getElementOf(); // element of is not allocated lazily so safe to do this.
	if (uElementOf != null)
	{
		unitCreated |= cloneUnit(uElementOf, cloneNum, false);
		Unit uElementOfClone = cloneMap.get(uElementOf);
		uClone.setElementOf(uElementOfClone);
		Unit.registerUnit(uElementOfClone);
	}
	if (u.hasArrayOf()) // array of is allocated lazily, so better check if it already has an array_of
	{
		Unit uArrayOf = u.getArrayOf();
		unitCreated |= this.cloneUnit(uArrayOf, cloneNum, false);
		Unit uArrayOfClone = cloneMap.get(uArrayOf);
		uClone.setArrayOf(uArrayOfClone);
		Unit.registerUnit(uArrayOfClone);
	}
*/
	return unitCreated;
}

/** generates a clone of this method summary. called only once for each clone. */
public MethodSummary cloneSummary(MethodInvoke mi)
{
	Util.ASSERT (!this.isClone); // only master method summaries can be cloned.

	int cloneNum = clones.size();
	MethodSummary newSummary = new MethodSummary(this.methodUnits, null, null);
	newSummary.cloneMap = new LinkedHashMap<Unit, Unit>();
	newSummary.methodInvoke = mi;
	// allocate new method param unit and return value units into newSummary
	newSummary.params = new MethodParamUnit[this.params.length];

	// setup params/retval for newSummary
	newSummary.createCloneUnitsIfNeeded(cloneNum);
	for (int i = 0; i < params.length; i++)
		newSummary.params[i] = (MethodParamUnit) newSummary.cloneMap.get(params[i]);

	newSummary.retVal = (ReturnValueUnit) newSummary.cloneMap.get(retVal);
	this.clones.add(newSummary);
	Util.ASSERT(this.clones.size() == (cloneNum+1));

	newSummary.multUnits = new ArrayList<MultUnit>();

	// set up unification between newSummary units
	discoverAllUnits();
	computeClusters();
	this.updateClone(newSummary, cloneNum);
	return newSummary;
}

private Set<Unit> repsOfCloneMapUnits;

/** sets up repsOfCloneMapUnits to contain reps of all the units that have been cloned */
private void computeRepsOfCloneMapUnits()
{
	if (repsOfCloneMapUnits == null)
		repsOfCloneMapUnits = new LinkedHashSet<Unit>();
	else
		repsOfCloneMapUnits.clear();

	for (Unit u : cloneMap.keySet())
		repsOfCloneMapUnits.add((Unit) u.find());
}

/** method to clone the given mult unit.
 * returns whether or not a clone unit was created. */
private boolean cloneMultUnitIfNeeded (MultUnit mu)
{
	// reps of clone map units must have been recently recomputed (before the last unifi)
	Util.ASSERT (repsOfCloneMapUnits != null);

	Unit a = mu.get_unit_a();
	Unit aRep = (Unit) a.find();
	Unit b = mu.get_unit_b();
	Unit bRep = (Unit) b.find();

	// should clone if at least one of a and b is in some equivalence class with some unit
	// that has already been cloned.
	// XXX: may need to iterate here till fixed point because we're adding to cloneMap inside this loop
	// e.g. if we have an expression: field1 * (field2 * p)
	// and p is a parameter that is cloned.
	// if we process the outer mult first, we find we don't need to clone it
	// because neither field1 nor (field2 * p) has been cloned.
	// after the inner mult, the mult unit (field2 * p) is cloned, so re-invoking the method
	// would correctly clone the outer mult as well.
	boolean shouldClone = (repsOfCloneMapUnits.contains(aRep) && !aRep.connectedToSingleUnit) ||
					      (repsOfCloneMapUnits.contains(bRep) && !bRep.connectedToSingleUnit);

	if (shouldClone)
	{
		// figure out who will represent a in the new mult unit
		Unit aClone = a;
		boolean found = false;
		if (repsOfCloneMapUnits.contains(aRep))
		{
			// there must be a u whose rep is the same as a's rep
			for (Unit u : cloneMap.keySet())
			{
				if (u.find().equals(aRep))
				{
					aClone = cloneMap.get(u);
					found = true;
					break;
				}
			}
			Util.ASSERT (found);
		}
		Util.ASSERT (aClone != null);

		// same thing for b
		found = false;
		Unit bClone = b;
		if (repsOfCloneMapUnits.contains(bRep))
		{
			for (Unit u : cloneMap.keySet())
			{
				if (u.find().equals(bRep))
				{
					bClone = cloneMap.get(u);
					found = true;
					break;
				}
			}
			Util.ASSERT (found);
		}
		Util.ASSERT (bClone != null);

		// either a or b must be different from its clones, otherwise why are we cloning ?
		Util.ASSERT (a != aClone || b != bClone);

		Fraction coeff = mu.get_coeff();
		MultUnit clone = new MultUnit (aClone, bClone, new Fraction(coeff.get_numerator(), coeff.get_denominator()));
        Unit.registerUnit(clone);
		cloneMap.put(mu, clone);
		repsOfCloneMapUnits.add((Unit) mu.find()); // update repsOfCloneMap
		// add the cloned mult unit to the caller's summary
		methodInvoke.getCallerMethodUnits().getMethodSummary().addMultUnit(clone);
		return true;
	}
	return false;
}

/** for this clone, create clone units of all params and retval (and dependents) of master summary
 * cloneMap is updated accordingly.
 * returns whether a new unit was created */
public boolean createCloneUnitsIfNeeded(int cloneNum)
{
	Util.ASSERT (this.isClone);

	boolean unitCreated = false;
	MethodSummary masterSummary = methodUnits.getMethodSummary();

	for (int i = 0; i < params.length; i++)
		unitCreated |= cloneUnit(masterSummary.params[i], cloneNum, true);

	unitCreated |= cloneUnit(masterSummary.retVal, cloneNum, true);

	// now onto mult units, which are a little tricky
	// we want to clone a mult unit if either of its terms is
	// a unit that has been cloned.
	// e.g. int m(int p) { return p * someField; } (p cloned, so mult unit also cloned)
	// or int m(int p1, int p2) { return p1 * p2; } (p1 and p2 cloned, so mult unit also cloned)
	// however, the mult unit may not involve the cloned unit directly, but maybe some other unit
	// in its equivalence class. e.g
	// e.g. int m(int p) { x = p; return x * someField; }
	// so we first compute the set of reps of all cloned units
	// if the terms of the mult unit have the same rep as a cloned unit U,
	// we use U in the cloned mult unit.
	// if we do clone the mult unit, we need to add it to the set of mult units in the calling method's summary

	computeRepsOfCloneMapUnits();

	for (MultUnit mu: masterSummary.multUnits)
	{
		if (cloneMap.get(mu) != null)
			continue;

	//	cloneMultUnitIfNeeded (mu);
	}

	return unitCreated;
}

/** adds u and its dependent units to allUnits */
private void addUnit(Unit u)
{
	if (u == null)
		return;
	if (allUnits.contains(u))
		return;

	allUnits.add(u);
	if (u.hasLengthUnit())
		addUnit(u.getLengthUnit());
	Unit uElementOf = u.getElementOf();
	if (uElementOf != null)
		addUnit(uElementOf);
	if (u.hasArrayOf())
		addUnit(u.getArrayOf());
}

/** computes all units reachable from params/retval and puts them into allUnits. */
private void discoverAllUnits()
{
	if (allUnits == null)
		allUnits = new LinkedHashSet<Unit>();
	else
		allUnits.clear(); // do we really need to clear ?

	for (int i = 0; i < params.length; i++)
		addUnit(this.params[i]);
	addUnit(this.retVal);
	allUnits.addAll(multUnits);
}

/* computes repToUnitsMap for this summary */
private void computeClusters()
{
	if (repToUnitsMap == null)
		repToUnitsMap = new LinkedHashMap<Unit, List<Unit>>();
	else
		repToUnitsMap.clear();

	int nextClusterNum = 0;
	for (Unit u : allUnits)
	{
		Unit rep = (Unit) u.find ();
		List<Unit> list = repToUnitsMap.get(rep);
		if (list == null)
		{
			rep.clusterNum = nextClusterNum++;
			list = new ArrayList<Unit> ();
			repToUnitsMap.put (rep, list);
			Util.ASSERT (repToUnitsMap.get(rep) == list);
		}
		list.add (u);
	}

	// assign cluster num to all units
	for (Map.Entry<Unit,List<Unit>> me : repToUnitsMap.entrySet())
	{
		Unit rep = me.getKey();
		List<Unit> list = me.getValue();
		for (Unit u : list)
			u.clusterNum = rep.clusterNum;
	}
}

/** returns the version of u in the clone. for fields and alloc units,
 * there is a single unit across the master method summary and all clones.
 * for other types of units, looks up the clone map
 */
private Unit getEquivUnitInClone(MethodSummary clone, Unit u)
{
	if ((u instanceof FieldUnit) || (u instanceof AllocUnit))
		return u;
	return clone.cloneMap.get(u);
}

/** updates clone mClone based on this (which must be a master). */
private boolean updateClone(MethodSummary mClone, int cloneNum)
{
	Util.ASSERT (!this.isClone);
	mClone.nUpdates++;
	boolean change = false;

	// check if any new units have to be created in this clone
	change |= mClone.createCloneUnitsIfNeeded(cloneNum);

	// now for each cluster in the master method summary,
	// unifi units in the same cluster in mClone
	for (Unit rep : this.repToUnitsMap.keySet())
	{
		boolean connectedToSingleUnit = rep.connectedToSingleUnit;

		Unit repClone = null; // repClone will be the first equiv unit we see in the clone
		for (Unit u : this.repToUnitsMap.get(rep))
		{
			Unit uClone = getEquivUnitInClone(mClone, u);
			if (uClone == null)
				continue;

			// optimization: check if u is connected to a cluster which also contains a field or alloc unit
			// if so we can directly unify uClone with u
			if (connectedToSingleUnit)
			{
				// if single unit, clone can just be unified with u
				if (uClone.find() != u.find())
				{
					if (logger.isLoggable(Level.FINE))
					{
						logger.fine ("Unifying because connected to a field or alloc unit: u = " + u + "\nuclone = " + uClone);
						logger.fine ("u.find() = " + u.find() + "\nuClone.find = " + uClone.find());
					}
					change = true;
					uClone.unify(u, mClone.methodInvoke.getBcp());
				}
			}
			else
			{
				if (repClone == null)
					repClone = uClone; // repClone not yet set, set it to the current uClone
				else
				{
					// uClone needs to be unified with repClone which has been set previously
					if (uClone.find() != repClone.find())
					{
						change = true;
						uClone.unify(repClone, mClone.methodInvoke.getBcp());
					}
				}
			}
		}
	}
	return change;
}

/** updates clones of this MMS, and if these clones cause
 * changes in the summaries of the calling method, then Q up the calling method */
public void updateClones()
{
	Util.ASSERT(!isClone); // should not call update clones on a clone, only on master summaries
	verify();
	discoverAllUnits();
	computeClusters();
	if (logger.isLoggable(Level.FINE))
		logger.fine ("Updating " + this.clones.size() + " clones for " + this);

	for (int i = 0; i < clones.size(); i++)
	{
		MethodSummary mClone = clones.get(i);
		boolean change = updateClone(mClone, i);

		if (change)
		{
			if (logger.isLoggable(Level.FINE))
				logger.fine ("Worklist: changed summary for clone " + i + " " + mClone + " [caller in method " + mClone.methodInvoke.getCallerMethodUnits() + "]");

			MethodUnits callerMU = mClone.methodInvoke.getCallerMethodUnits();
			ContextSensitiveAnalysis.addToWorklist(callerMU);
		}
	}
}

public String toString()
{
	StringBuilder sb = new StringBuilder();
	sb.append ("Method summary for " + methodUnits + "\n");
	discoverAllUnits();
	computeClusters();
	sb.append (allUnits.size() + " units " + repToUnitsMap.size() + " reps " + nUpdates + " updates\n");
	int clusterNum = 0;
	for (Unit rep : this.repToUnitsMap.keySet())
	{
		sb.append ("\nCluster " + (++clusterNum) + ":\n");
		int unitNum = 0;
		for (Unit u : this.repToUnitsMap.get(rep))
		{
			++unitNum;
			sb.append (clusterNum + "." + unitNum + ": " + u + "\n");
		}
	}
	return sb.toString();
}

public void verify()
{
	Util.ASSERT (isClone == (cloneMap != null)); // clones must have a map, master's must not
	Util.ASSERT (isClone == (methodInvoke != null)); // clones must have a invoke, master's must not

	if (clones != null)
		Util.ASSERT (!isClone); // clones cannot have other clones

	if (!isClone)
	{
		// one to one relationship between a method unit and its master summary.
		Util.ASSERT (this == methodUnits.getMethodSummary());
		// params and retval units must be the same as those in method units for master method summaries
		Util.ASSERT (params == methodUnits.get_param_units());
		Util.ASSERT (retVal == methodUnits.get_return_value_unit());

		if (clones != null)
		{
			// verify each clone
			for (MethodSummary clone: clones)
			{
				Util.ASSERT (clone.isClone);
				Util.ASSERT (clone.methodUnits == this.methodUnits);
				clone.verify();
			}
		}
	}
}
}
