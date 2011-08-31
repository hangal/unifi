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


import java.util.*;
import unifi.*;
import unifi.drivers.Analyze;
import unifi.units.Unit;

public class ConstraintSolver {

public ConstraintSet _cs;

// returns a constraint which can act as a formula for u
private Constraint select_formula_constraint (Unit u)
{
    Collection<Constraint> all_constraints_for_this_unit = _cs.map().get(u);
    // return if this unit does not have any constraints
    if (all_constraints_for_this_unit == null)
    {
        System.out.println ("No constraints for unit " + u.short_toString());
        return null;
    }

    Constraint constraint_found = null;
    for (Constraint c : all_constraints_for_this_unit)
    {
    // maybe we can have some rules for which of the constraints
    // to choose for the canonical form. Perhaps one which has
    // n_dimensions == 1 or 2 ?
        if (!c.is_formula())
        {
            constraint_found = c;
            break;
        }
    }
    return constraint_found;
}

/** eliminate the unit u from all constraints
 * by selecting one of the constraints to represent
 * the formula for u, then replace htat unit with its
 * formula in all other constraints.
 */
private void eliminate (Unit u)
{
    Constraint c = select_formula_constraint (u);
    // if this unit is not involved in a non-formula constraint,
    // it does not need any explicit formula, or substitution
    // e.g. consider the constraint A.B.C = 0
    // where A, B, C don't appear anywhere else.
    // then after adding the formula A->[A.B.C = 0]
    // there are no formula constraints left for B and C.
    if (c == null)
        return;

    c.verify();

    _cs.add_formula (u, c); // for unit u, constraint c is the official form
    c.verify();
    Dimensions d = c.dimensions().solve_for (u);
    c.verify(); // do not call c.verify here, because c may have null dimensions - it's waiting to be removed
    // now rewrite all other constraints with with the solved version of u.
    _cs.rewrite_unit (u, d);
    _cs.verify(); // TODO: EXPENSIVE - remove
}

private void draw_inferences ()
{
    // this iteration should eventually be in a canonical order
    for (Unit u : _cs.sorted_all_units())
    {
        System.out.println ("Eliminating " + u);
        eliminate (u);
    }

    _cs.mark_dimensionless_units();
}

public void solve_constraints (UnitCollection uc)
{
    _cs = uc.get_mult_constraints();
    // we should probably remove all dimensionless units here!
    _cs.verify();

    draw_inferences();
    System.out.println ("***************** CONSTRAINTS AFTER solving");
    System.out.println (_cs);
}

// stub main method to read directly from a .units file and perform only the constraint solving part of it */
public static void main(String args[])
{
	Analyze.read_unit_collection_file();
	UnitCollection uc = Unit._current_unit_collection;

	uc.prepare_to_solve(null);
	System.out.println ("***************** Compound constraints AFTER prepare to solve\n" + uc.get_mult_constraints());
	uc.solve_constraints();
}

}
