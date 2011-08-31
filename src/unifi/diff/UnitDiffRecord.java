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

package unifi.diff;


import java.util.*;
import org.apache.bcel.generic.*;

import unifi.UnificationEvent;
import unifi.UnitCollection;
import unifi.UnitCollection.PathInfo;
import unifi.units.FieldUnit;
import unifi.units.Unit;
import unifi.util.Util;

/** class which tracks a single diff record (a single set of units
 * which got merged in one version but not the other)
 */
public class UnitDiffRecord extends DiffRecord implements Comparable {

private Map<Unit, List<Unit>> repToClassMap;
private List<UnificationEvent> interesting_unif_events = new ArrayList<UnificationEvent>();
private List<UnificationEvent> maybe_interesting_unif_events = new ArrayList<UnificationEvent>();
private UnitCollection uc; // unit collection where the reps belong.
private UnitCollection uc_unmerged; // unit collection where this unit is originally unmerged.

// rep_to_class_map is a Unit->HashSet mapping of
// unit reps to equivalence classes which have
// got merged (these classes contain only units common
// to both unitcollections we are comparing!). we want to 
// select the interesting
// unification events from unif_events which merge
// units which should have been in different eq. classes
// according to rep_to_class_map.

// this class has a natural ordering that is inconsistent with equals
// because it may return - for non equals() objects
public int compareTo(Object o)
{
    // rank unit diff record before any other kind of diff record
    if (!(o instanceof UnitDiffRecord))
        return -1;

    UnitDiffRecord other = (UnitDiffRecord) o;
    if (is_primitive_type() && !other.is_primitive_type())
        return -1;
    if (!is_primitive_type() && other.is_primitive_type())
        return 1;

    int x1 = interesting_unif_events.size();
    int x2 = other.interesting_unif_events.size();
    // if this has interesting events and other does not, return -1
    if ((x1 > 0) && (x2 == 0))
        return -1;
    // if both have a different # of interesting events, then return 
    // -ve value if this has fewer interesting events.
    if ((x1 - x2) != 0)
        return x1 - x2;

    x1 = maybe_interesting_unif_events.size();
    x2 = other.maybe_interesting_unif_events.size();
    if ((x1 > 0) && (x2 == 0))
        return -1;
    if ((x1 - x2) != 0)
        return x1 - x2;

    return 0;
}

/** returns whether this is set of units is associated with a primitive type */
public boolean is_primitive_type() 
{
    // just get the first unit we can find and see if it is of primitive type
    Unit u = repToClassMap.keySet().iterator().next();
    return u.isPrimitiveType();
}

// m is a map of reps to equiv classes in list form, e.g. U1->{U1,U2}, U3->{U3,U4}
// where this diff record captures the fact that {u1, u2} got merged
// newly with {u3, u4}
// uc_unmerged is the other UnitCollection where these units didn't get merged -- used only
// when printing the path (we want to know which edges along the path are a jump
// in 2 different classes when not merged)
public UnitDiffRecord(Map<Unit, List<Unit>> m, UnitCollection uc, UnitCollection uc_unmerged) 
{
    this.uc_unmerged = uc_unmerged;
    // remember to clone, caller's Map will get cleared.
    repToClassMap = (Map<Unit, List<Unit>>) ((HashMap) m).clone();
    Util.ASSERT (repToClassMap.size() > 1);

    // build up unit to class map for each unit involved.
    Map<Unit,List<Unit>> unit_to_class_map = new LinkedHashMap<Unit,List<Unit>>();
    for (Map.Entry<Unit, List<Unit>> me : repToClassMap.entrySet())
    {
        Unit rep = me.getKey();
        List<Unit> list = me.getValue();
        for (Unit u : list)
        {
            // same unit should not exist in different classes
            Util.ASSERT (unit_to_class_map.get(u) == null);
            // all units must be in the same union-find class
            Util.ASSERT (u.find() == rep.find());
            unit_to_class_map.put (u, list);
        }
    }

    Collection<UnificationEvent> unif_events = uc.get_events();

    // now unit_to_class_map is Map[each unit -> List<all units in its eq. class>] 

    // look for unification events which directly unify interesting units
    // in different equivalence classes.
    // also compute "maybe" interesting events, which are events which merge an interesting
    // unit with a non-interesting unit.
    // Note: there may be unification between an interesting and a
    // non-interesting unit because the unif_events contains ALL unifications,
    // not just between unifications common to uc1 and uc2, while 
    // rep_to_class_map
    // contains only units of interest (common to uc1 and uc2)
    for (UnificationEvent ue : unif_events)
    {
        // if this unif event pairs up 2 units in different classes,
        // then print it out
        Unit unit_a = ue.get_unit_a();
        Unit unit_b = ue.get_unit_b();
        Object eq_class_a = unit_to_class_map.get (unit_a);
        Object eq_class_b = unit_to_class_map.get (unit_b);

        if ((eq_class_a != null) && (eq_class_b != null) && 
            (eq_class_a != eq_class_b))
        {
            interesting_unif_events.add (ue);
        }

        if ((eq_class_a != null) ^ (eq_class_b != null))
        {   // one interesting, one non-interesting
            // this only gets non-interesting paths 
            // between a and b of length 1
            // will probably need to fix
            maybe_interesting_unif_events.add (ue);
        }
    }

    this.uc = uc;
    Collections.sort(interesting_unif_events);
    Collections.sort(maybe_interesting_unif_events);
    verify();
}

public void verify ()
{
    Util.ASSERT (repToClassMap.size() > 1);
}

public String get_path_str(Unit src, Unit target)
{
    StringBuilder sb = new StringBuilder();
    sb.append ("looking for path from " + src + " to " + target + "\n");
    List<UnitCollection.PathInfo> path = uc.find_path(src, target);
    if (path == null)
        sb.append ("No path exists\n");
    else
    {
        int count = 0;
        for (UnitCollection.PathInfo p : path)
        {
            UnificationEvent ue = p.get_event();
            sb.append (++count + ". " + p.get_event());
            Unit a = ue.get_unit_a();
            Unit b = ue.get_unit_b();
            a = uc_unmerged.get_equiv_unit(a);
            b = uc_unmerged.get_equiv_unit(b);
            if ((a != null) && (b != null))
            {
                if (a.find() != b.find())
                    sb.append (" [JUMP] ");
            }
            else
            {
                // this edge in the path is not in uc_unmerged
                sb.append (" [OUTSIDE] ");
            }

            sb.append ("\n");
        }
    }
    return sb.toString();
}

/* returns whether this diff record merges more than 2 classes with a field in them - 
 * if so, it might be more interesting for a pure field analysis */
private boolean mergesFields()
{
	int nClassesWithFieldUnit = 0;
	
	for (List<Unit> list : repToClassMap.values())
	{
		for (Unit u : list)
			if (u instanceof FieldUnit)
			{
				nClassesWithFieldUnit++;
				break;
			}
	}
	return (nClassesWithFieldUnit >= 2);
}

public String toString()
{
    verify();
    StringBuffer sb = new StringBuffer();
    // classes represents the different equiv. classes in uc2 which got merged in uc1
    // is Map[Rep, UnitSet]
    if (mergesFields())
    		sb.append ("--IMPORTANT-- ");
    
    sb.append ("The following " + repToClassMap.keySet().size() + " sets of units got merged!\n");

    // sort it first so that the equivalence classes 
    // which got merged are written out in descending order of size.
    // (convenient for user to parse message)
    List<Unit> reps = new ArrayList<Unit>();
    reps.addAll (repToClassMap.keySet());
    Collections.sort (reps, new Comparator() 
                         { public int compare (Object o1, Object o2) { 
                           int s1 = ((Collection) repToClassMap.get(o1)).size(); 
                           int s2 = ((Collection) repToClassMap.get(o2)).size(); 
                           return s2 - s1;
                         }});

    Util.ASSERT (reps.size() == repToClassMap.size());
    Util.ASSERT (reps.size() > 1);

    int count1 = 1;
    for (Unit rep : reps) 
    {
        sb.append (count1 + ". -----------------------\n");
        count1++;
        List<Unit> units = repToClassMap.get (rep);
        for (Unit u1 : units)
            sb.append ("  " + u1 + "\n");
    }

    // print path from first unit of each class to first unit of the next class
    for (int i = 0; i < (reps.size()-1); i++)
    {
        Unit u1 = reps.get(i);
        Unit u2 = reps.get(i+1);
        List<Unit> class1 = repToClassMap.get(u1);
        List<Unit> class2 = repToClassMap.get(u2);
        Unit first_unit_in_class1 = class1.get(0);
        Unit first_unit_in_class2 = class2.get(0);
        sb.append ("PATH " + (i+1) + "-" + (i+2) + ":\n" + get_path_str(uc.get_equiv_unit(first_unit_in_class1), uc.get_equiv_unit(first_unit_in_class2)) + "------------------------------------------------------------------------------------------\n");
    }

    if (interesting_unif_events.size() > 0)
    {
        sb.append ("**************\n" + interesting_unif_events.size() 
                 + " DIRECT unifications found:\n");
        int count2 = 1;
        for (Iterator it4 = interesting_unif_events.iterator(); it4.hasNext(); )
        {
            sb.append ("DC" + count2 + ". " + it4.next() + "\n");
            count2++;
        }
        sb.append ("\n");
    }
    else 
        sb.append ("No interesting unif events.");

    if (maybe_interesting_unif_events.size() > 0)
    {
        sb.append ("**************\n" + maybe_interesting_unif_events.size() + " INDIRECT unifications found:\n");
        int count2 = 1;
        for (Iterator it4 = maybe_interesting_unif_events.iterator(); it4.hasNext(); )
        {
            sb.append ("IC" + count2 + ". " + it4.next() + "\n");
            count2++;
        }
        sb.append ("\n");
    }
    else 
        sb.append ("No maybe-interesting unif events.");

    return sb.toString();
}
}
