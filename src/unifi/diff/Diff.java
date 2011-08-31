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

import java.io.*;
import java.util.*;
import org.apache.bcel.generic.*;

import unifi.UnitCollection;
import unifi.solver.*;
import unifi.units.MethodParamUnit;
import unifi.units.ReturnValueUnit;
import unifi.units.Unit;
import unifi.util.Util;

class Diff {

private static Set<Unit> uc1_common_units = new LinkedHashSet<Unit>();
private static Set<Unit> uc2_common_units = new LinkedHashSet<Unit>();
private static long startTime = System.currentTimeMillis(), endTime;

private static Map<Class, Integer> computeHistogram(Collection c)
{
    Map<Class, Integer> histogram = new HashMap<Class, Integer>();
    for (Object o : c)
    {
        Class cl = o.getClass();
        if (histogram.get(cl) == null)
            histogram.put(cl,1);
        else
            histogram.put (cl, histogram.get(cl)+1);
    }

    return histogram;
}
    
// prints class distribution of the given collection
private static void printClassHistogram(Collection c1, Collection c2, String prefix)
{
    Map<Class, Integer> hist1 = computeHistogram(c1);
    Map<Class, Integer> hist2 = computeHistogram(c2);

    for (Map.Entry<Class, Integer> me: hist1.entrySet())
    {
        Class cl = me.getKey();
        int i = me.getValue();
        System.out.println ("STAT: " + prefix + "." + cl.getName() + " : " + i + " of " + hist2.get(cl));
    }
}

public static void main(String args[]) throws IOException, ClassNotFoundException
{
    System.out.print ("Running: java unifi.diff ");
    for (String arg : args) { System.out.print (arg + " "); }
    System.out.println ();
    System.out.println ("CLASSPATH is: " + System.getProperty ("java.class.path"));
    System.out.println ("Current directory is: " + System.getProperty ("user.dir"));

    ObjectInputStream oos1 = new ObjectInputStream(new FileInputStream(args[0]));
    ObjectInputStream oos2 = new ObjectInputStream(new FileInputStream(args[1]));

    // unit collections 1 and 2
    UnitCollection uc1 = (UnitCollection) oos1.readObject ();
    UnitCollection uc2 = (UnitCollection) oos2.readObject ();
    
    oos1.close();
    oos2.close();

    // unification events 1 and 2
    // Collection ue1 = (Collection) oos1.readObject ();
    // Collection ue2 = (Collection) oos2.readObject ();
    Collection ue1 = uc1.get_events();
    Collection ue2 = uc2.get_events();

//    uc1.print_units();
    uc1.remove_phi_units();
    uc1.remove_clone_units();
    uc1.compute_reps();
//    uc2.print_units();
    uc2.remove_phi_units();
    uc2.remove_clone_units();
    uc2.compute_reps();

    resolve_constraints(uc1, uc2);
    /*
      uc1.remove_phi_units();
    uc1.remove_clone_units();
    uc2.remove_phi_units();
    uc2.remove_clone_units();
*/
    System.out.println ("Units which were merged in " + args[0] + " but not in " + args[1]);
    compare (uc1, uc2, "-", ue1);
    System.out.println ("-----------------------------------------------------------------------------");
    System.out.println ("Units which were merged in " + args[1] + " but not in " + args[0]);
    compare (uc2, uc1, "+", ue2);
    System.out.println ("*****************************************************************************");

    // compare formula's here.
    // compare_formulas (uc1, uc2);

    endTime = System.currentTimeMillis();
    System.out.println ("STAT: ElapsedTimeMillis: " + (endTime-startTime));
}

private static void resolve_constraints (UnitCollection uc1, UnitCollection uc2)
{
    for (Unit u1 : uc1.get_units())
    {
        Unit u2 = uc2.get_equiv_unit(u1);
        if (u2 != null)
        {
            uc1_common_units.add(u1);
            uc2_common_units.add(u2);
        }
    }

    int uc1_size = uc1.get_units().size();
    int uc2_size = uc2.get_units().size();
    int uc1_common_size = uc1_common_units.size();
    int uc2_common_size = uc2_common_units.size();
    int uc1_common_pct = (uc1_size != 0) ? (100 * uc1_common_size) / uc1_size : 0;
    int uc2_common_pct = (uc2_size != 0) ? (100 * uc2_common_size) / uc2_size : 0;

    // mult unit are never compared, so this is never 100%
    System.out.println ("STAT: uc1_common_pct: " + uc1_common_pct);
    System.out.println ("STAT: uc1_common_size: " + uc1_common_size);
    System.out.println ("STAT: uc1_size: " + uc1_size);

    System.out.println ("STAT: uc2_common_pct: " + uc2_common_pct);
    System.out.println ("STAT: uc2_common_size: " + uc2_common_size);
    System.out.println ("STAT: uc2_size: " + uc2_size);

    System.out.println ("uc1 common histogram:");
    printClassHistogram(uc1_common_units, uc1.get_units(), "uc1");
    System.out.println ("uc2 common histogram:");
    printClassHistogram(uc2_common_units, uc2.get_units(), "uc2");

    uc1.prepare_to_solve(uc1_common_units);
    uc2.prepare_to_solve(uc2_common_units);
    uc1.solve_constraints();
    uc2.solve_constraints();
}

// compares uc1 and uc2, printing out units which got 
// merged in uc1, but not in uc2. also print associated 
// unification events in ue1. merge messages are printed 
// with the given prefix.
@SuppressWarnings("unchecked")
private static void compare (UnitCollection uc1, UnitCollection uc2, String prefix, Collection ue1)
{
    // final results of all the unit diff records
    List<DiffRecord> results = new ArrayList<DiffRecord>();

    Map m1 = uc1.get_reps();
    Collection reps1 = uc1.get_all_unique_units();
    final Map<Unit,Set<Unit>> classes = new LinkedHashMap<Unit, Set<Unit>>();

    boolean only_prims = (System.getProperty("unifi.track.references") == null);
    for (Iterator it = reps1.iterator(); it.hasNext(); )
    {
        Unit rep1 = (Unit) it.next();
        Type t = rep1.getType();

        // if type is null, usually means object type
        if (only_prims)
        {
            if (t == null)
                continue;
            // consider only strings and basic types if only_prims is defined
            if (!(t instanceof BasicType) && 
                 !t.getSignature().equals("Ljava/lang/String;"))
            {
                continue;
            }
        }

        Collection units_for_this_rep = (Collection) m1.get(rep1); // all units whose rep. is rep1

        // System.out.println ("rep = " + rep1);
        classes.clear();

        /* "interesting units" are units present in both collections.
           we will take all units represented by rep1, locate
           the corresponding units in uc2, and see if all these units
           map to more than 1 class in uc2. If so, uc1 merged
           some units which were unmerged in uc2. */
           
        for (Iterator it1 = units_for_this_rep.iterator(); it1.hasNext(); )
        {
            Unit u1 = (Unit) it1.next();
            Util.ASSERT (u1.find() == rep1); // to track down some strange behaviour
            Util.ASSERT     (!((u1 instanceof MethodParamUnit) && ((MethodParamUnit)u1).getCloneNum() >= 0));
            Util.ASSERT     (!((u1 instanceof ReturnValueUnit) && ((ReturnValueUnit)u1).getCloneNum() >= 0));

            // System.out.println ("  subunit = " + u1);
            Unit u2 = uc2.get_equiv_unit (u1);
            
            if (u2 != null)
            {
                Util.ASSERT(u1.equals(u2));
//                System.out.println ("u2 = " + u2);
                Unit rep2 = (Unit) u2.find();
                // note: rep2 itself may not have an equiv unit in uc1
                Set<Unit> s = classes.get(rep2);
                if (s == null)
                {
                    s = new LinkedHashSet<Unit>();
                    classes.put (rep2, s);
                }
                s.add (u2);
            }
        }

        if (classes.keySet().size() <= 1)
            continue;

        // now convert the classes which is unit->Set of units
        // into unit -> list of units.
        Map<Unit, List<Unit>> new_classes = new HashMap<Unit, List<Unit>>();
        for (Set<Unit> s : classes.values())
        {
            List<Unit> list = new ArrayList<Unit>();
            list.addAll(s);
            Collections.sort(list);
            new_classes.put(list.get(0), list);
        }

        results.add(new UnitDiffRecord(new_classes, uc1, uc2));
    }

    results.addAll(compare_formulas(uc1, uc2));

    Collections.sort (results);

    System.out.println (results.size() + " unit diff records");
    int count = 1;
    for (DiffRecord udr : results)
    {
	System.out.println ("Unit diff record " + count);
        System.out.println (prefix + " " + udr);
        count++;
    }
}

public static List<DiffRecord> compare_formulas(UnitCollection uc1, UnitCollection uc2)
{
    List<DiffRecord> results = new ArrayList<DiffRecord>();

    ConstraintSet cs1 = uc1.get_mult_constraints();
    ConstraintSet cs2 = uc2.get_mult_constraints();
    Map<Unit,Constraint> formulas1 = cs1.formulas();
    Map<Unit,Constraint> formulas2 = cs2.formulas();

    for (Map.Entry<Unit,Constraint> me : formulas1.entrySet())
    {
        Unit u1 = me.getKey();
        Constraint c1 = me.getValue();
        if (c1 == null)
            continue;

        Unit u2 = uc2.get_equiv_unit(u1);
        if (u2 == null)
            continue;
        Constraint c2 = formulas2.get(u2);
        if (c2 == null)
            continue;

        boolean u1_is_dimension_less = u1.isDimensionLess();
        boolean u2_is_dimension_less = u2.isDimensionLess();

        if (u1_is_dimension_less != u2_is_dimension_less)
        {
            results.add (new DimensionLessUnitDiffRecord (u1, u2, u1_is_dimension_less, u2_is_dimension_less));
        }

        // c1/c2_units will contain all the non-dimensionless units in c1/c2
        List<Unit> c1_units = new ArrayList<Unit>();
        List<Unit> c2_units = new ArrayList<Unit>();
        for (Iterator<Dimension> it = c1.dimensions().iterator(); it.hasNext(); )
        {
            Unit u = it.next().unit();
            if (!u.isDimensionLess())
                c1_units.add(u);
        }
        for (Iterator<Dimension> it = c2.dimensions().iterator(); it.hasNext(); )
        {
            Unit u = it.next().unit();
            if (!u.isDimensionLess())
                c2_units.add(u);
        }

        boolean all_c1_units_in_c2 = true;
        for (Unit u : c1_units)
        {
            Unit equiv_unit = uc2.get_equiv_unit(u);
            if ((equiv_unit == null) || !c2_units.contains(equiv_unit))
            {
                all_c1_units_in_c2 = false;
                break;
            }
        }

        boolean all_c2_units_in_c1 = true;
        for (Unit u : c2_units)
        {
            Unit equiv_unit = uc1.get_equiv_unit(u);
            if ((equiv_unit == null) || !c1_units.contains(equiv_unit))
            {
                all_c2_units_in_c1 = false;
                break;
            }
        }

        if (!(all_c2_units_in_c1 && all_c1_units_in_c2))
            continue; // not comparable

        Util.ASSERT (c1_units.size() == c2_units.size());

        // normalize the exponents of u1 in c1/c2 to 1
        Dimensions dims1 = c1.dimensions().solve_for(u1);
        Dimensions dims2 = c2.dimensions().solve_for(uc2.get_equiv_unit(u1));

        // for each dimension in dims1/2, check if the exponents are the same
        for (Iterator<Dimension> it = dims1.iterator(); it.hasNext(); )
        {
            Dimension d1 = it.next();
            Unit this_u = d1.unit();

            Unit looking_for = uc2.get_equiv_unit(this_u);
            Dimension d2 = null;
            for (Iterator<Dimension> it2 = dims2.iterator(); it2.hasNext(); )
            {
                Dimension next = it2.next();
                System.out.println ("Comparing with " + next);
                System.out.println (next.unit() == looking_for);
                System.out.println (next.unit().equals(looking_for));
                if (next.unit().equals(looking_for))
                {
                    d2 = next;
                    break;
                }
            }

            if (d2 == null)
            {
                System.out.println ("c1 = " + c1);
                System.out.println ("c2 = " + c2);
                System.out.println ("dims1 = " + dims1);
                System.out.println ("dims2 = " + dims2);
                System.out.println ("d1 = " + d1);
                Util.die();
            }
            System.out.println ("d2.unit = " + d2.unit());

            if (!d1.exponent().equals (d2.exponent()))
            {
                FormulaDiffRecord fdr = new FormulaDiffRecord(u1, c1, c2, d1, d2);
                results.add(fdr);
                System.out.println (fdr);
            }
        }
        // are_dimensions_equal(uc1, uc2, c1, c2);
    }
    return results;
}

}

