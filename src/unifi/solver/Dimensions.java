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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import unifi.units.MultUnit;
import unifi.units.Unit;
import unifi.util.Util;

/**
 * a Dimensions object is a multiple of dimension objects.
 * each dimension object is a unit raised to a fraction.
 */
public class Dimensions implements Serializable, Iterable<Dimension> {

private Collection<Dimension> _dims = new ArrayList<Dimension>();

public Dimensions () { /* */ }

// creates a dimension s object u1 * u2^coeff
public Dimensions (Unit u1, Unit u2, int coeff)
{
	Util.ASSERT(coeff != 0);
    _dims.add (new Dimension(u1, 1));
    merge_dim (u2, new Fraction(coeff));
}

// creates a dimension s object u1 * u2^coeff
public Dimensions (Unit u1, Unit u2, Fraction coeff)
{
	Util.ASSERT(!coeff.equals_zero());
    _dims.add (new Dimension(u1, 1));
    merge_dim (u2, coeff);
}

// creates a dimension s object u^1
public Dimensions (Unit u)
{
    _dims.add (new Dimension(u, 1));
}

public int size() { return _dims.size(); }

public int get_n_dimensions () { return _dims.size(); }

/** flattens the dimensions so it doesn't have any mult units,
 * only simple units */
public void flatten()
{
	verify();
    Collection<Dimension> old_dims = _dims;
    _dims = new ArrayList<Dimension>();

    for (Dimension d : old_dims)
    {
    	Util.ASSERT(!d.exponent().equals_zero());
        add_flattened (d.unit(), d.exponent());
    }

    // ensure there are no mult units remaining
    for (Dimension d :_dims)
    {
        Util.ASSERT (!(d.unit() instanceof MultUnit));
        Util.ASSERT(!d.exponent().equals_zero());
    }

    verify();
}

// clones this object, creating a fresh copy of _dims
public Object clone ()
{
    Dimensions d = new Dimensions();
    for (Iterator it = _dims.iterator(); it.hasNext(); )
    {
        Dimension d1 = (Dimension) it.next();
        d._dims.add ((Dimension) d1.clone());
    }
    return d;
}

public Iterator<Dimension> iterator()
{
    return _dims.iterator();
}

// scales each dimension's exponent by a factor of x
public void scale (int x)
{
	Util.ASSERT(x != 0);
    Fraction f = new Fraction(x);
    for (Dimension d : _dims)
        d.exponent().multiply(f);
}

/** adds unit u with fraction f, recursively flattening
 * u if it's a multunit.
 */
private void add_flattened(Unit u, Fraction f)
{
	verify();
    if (u instanceof MultUnit)
    {
        MultUnit mu = (MultUnit) u;

        // mu is unit_a * unit_b^coeff
        Unit unit_a = mu.get_unit_a();
        Unit unit_b = mu.get_unit_b();

        add_flattened (unit_a, f);

        // Fraction f1 = new Fraction(coeff);
        // be careful not to modify original coeff passed in - was a bug earlier!
        Fraction coeff = (Fraction) mu.get_coeff().clone();
        coeff.multiply(f);
    }
    else
        merge_dim (u, f);
}

/** adds a dimension to this */
public void merge_dim (Dimension d)
{
    merge_dim (d.unit(), d.exponent());
}

/** adds a dimension with unit u and exp f to this */
public void merge_dim (Unit u, Fraction f)
{
    verify();

    Util.ASSERT (f != null);
    // f can be zero sometimes, if so don't add it to this, breaks the invariant that no exponent is 0
    if (f.equals_zero())
    	return;

    Dimension x = null;

    boolean found = false;
    for (Iterator it = _dims.iterator(); it.hasNext(); )
    {
        x = (Dimension) it.next();
    	Util.ASSERT((x.unit() == u) == (x.unit().equals(u)));
    	Util.ASSERT (x.equals(x));
    	if (x.unit() == u)
        {
            found = true;
            break;
        }
    }

    if (!found)
    {
    	Util.ASSERT(!f.equals_zero());
        _dims.add (new Dimension (u, f));
    }
    else
    {
        x.exponent().add(f);
        if (x.exponent().equals_zero())
        {
        	boolean removed = _dims.remove (x);
        	Util.ASSERT(removed);
        }
    }
    verify();
}

/** merge this dims with another dims d.
 * coeffs of dims d are multiplied by mult. this may be empty when this method
 * is called, but d cannot be
 */
public void merge_dims (Dimensions d, Fraction mult)
{
    d.verify();
    for (Iterator it = d.iterator(); it.hasNext(); )
    {
        Dimension dim = (Dimension) it.next();
        dim = (Dimension) dim.clone();
        Unit u = dim.unit();
        dim.exponent().multiply (mult);
        merge_dim (u, dim.exponent());
//        System.out.println ("after merge of " + u + "^" + dim.exponent() + ", this = " + this);
    }

    this.verify();
}

// recomputes this dimensions object with reps in place
// of the original units.
public void update_with_reps ()
{
    Map<Unit,Fraction> map = new LinkedHashMap<Unit,Fraction>();

    for (Iterator it = _dims.iterator(); it.hasNext(); )
    {
        Dimension d = (Dimension) it.next();
        Unit u = d.unit();
        u = (Unit) u.find();
        /*
        if (u instanceof MultUnit)
        {
            MultUnit mu = (MultUnit) u;
            Unit a = mu.get_unit_a();
            Unit b = mu.get_unit_b();
        }
        else
        */
        {
        Fraction f = d.exponent();
        Fraction f1 = map.get(u);
        if (f1 == null)
            map.put (u, f);
        else
           f1.add (f);
        }
    }

    _dims.clear();

    for (Map.Entry<Unit,Fraction> me : map.entrySet())
    {
        Unit u = me.getKey();
        Fraction f = me.getValue();
        if (f.equals_zero())
        	System.out.println ("Dropping exp 0 unit: " + u);
//        _dims.add (new Dimension (u, f));
        merge_dim (u, f);
    }
}

/** returns true iff this dimensions includes unit u (with non-0 exponent)
as a component
*/
public boolean is_derived_from (Unit u)
{
    for (Dimension d : _dims)
    {
        if (d.unit().equals (u))
            return true;
    }
    return false;
}

// returns exponent of this unit if it is present in this Dimensions object; if it is not, returns null.
public Fraction exponent_of (Unit u)
{
    // get exponent of the unit u in this dimensions
    for (Dimension dim : _dims)
        if (dim.unit() == u)
            return dim.exponent();
    return null;
}

/** returns a new dimensions object, eliminating unit u
// from this dimensions object
// e.g. (A^2.B.C^3.D^-2).solve_for (A) will give
// B^-1/2.C-^3/2.D^1
// May return an empty (but not null) dimensions object e.g.
// [A^2].solve_for (A) will return empty
*/
public Dimensions solve_for (Unit u)
{
    Dimensions new_dims = new Dimensions();
    Fraction exp = exponent_of (u);
    Util.ASSERT (exp != null);

    // exp is the exponent of u in this
    // for all other units u, add U^-(1/exp) to the returned dimensions
    for (Dimension dim : _dims)
    {
        if (dim.unit() != u)
        {
            dim = (Dimension) dim.clone();
            dim.exponent().divide (exp);
            dim.exponent().multiply (new Fraction(-1));
            new_dims.merge_dim (dim);
        }
    }

    return new_dims;
}

/** replace unit u in this dimensions with dims */
public void rewrite (Unit u, Dimensions replace_dims)
{
    Fraction exp = null;

    // remove the dim corresponding to unit u
    for (Dimension dim : _dims)
    {
        if (dim.unit().equals (u))
        {
            exp = dim.exponent();
            // safe to remove during iteration because
            // we are going to break out
            _dims.remove (dim);
            break;
        }
    }
    Util.ASSERT (exp != null);

    // and replace with the dimensions in d, raised to the
    // same exponent u had
    merge_dims (replace_dims, exp);
}

// remove the unit u from this object, regardless of exponent.
// returns true if this dimensions object is still significant,
// false otherwise
// u *must* be part of this object
public boolean delete_unit (Unit u)
{
    boolean found = false;

    for (Iterator it = _dims.iterator(); it.hasNext(); )
    {
        Dimension dim = (Dimension) it.next();
        Unit u1 = dim.unit();
        if (u1.equals(u))
        {
            _dims.remove (dim);
            found = true;
            break;
        }
    }

    Util.ASSERT (found, "map is screwed up, trying to remove unit from a dimensions object which it is not a part of");

    return (_dims.size() > 0);
}

public int hashCode ()
{
    return 0;
}

public String neat_toString()
{
    StringBuilder sb = new StringBuilder();
    for (Iterator it = _dims.iterator(); it.hasNext(); )
        sb.append ("  " + ((Dimension)it.next()).reverse_short_toString() + "\n");
    return sb.toString();
}

public String toString()
{
    StringBuffer sb = new StringBuffer("[");

    for (Iterator it = _dims.iterator(); it.hasNext(); )
    {
        sb.append (((Dimension)it.next()).short_toString());
        if (it.hasNext())
            sb.append (" * ");
    }
    sb.append ("]");

    return sb.toString();
}

public void verify ()
{
    Set<Unit> seenUnits = new LinkedHashSet<Unit>();

//    Util.ASSERT (_dims.size() > 0);

    for (Iterator it = _dims.iterator(); it.hasNext(); )
    {
        Dimension d = (Dimension) it.next();
        Unit u = d.unit();
        if (d.exponent().equals_zero())
            Util.die ("FAIL: exponent is 0 for unit " + u + " in dimensions: \n" + d); // Important invariant

        if (seenUnits.contains (u))
        {
        	System.out.println ("dims.size = " + _dims.size() + "\n");
        	System.out.println ("u equals itself = " + u.equals(u) + "\n");
        	Util.die ("duplicate unit " + u + " in dimensions: \n" + this);
        }
        seenUnits.add (u);
    }
}

}
