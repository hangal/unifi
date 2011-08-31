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

package unifi.units;

import unifi.drivers.Analyze;
import unifi.solver.*;
import unifi.util.Util;

public class MultUnit extends Unit {

private Unit _a, _b;
private Fraction _coeff_b;

// a and b both should not be null
// coeff = 1 for mult, -1 for div
public MultUnit (Unit a, Unit b, Fraction coeff)
{
    super (a.getType()); // Java type of the mult is the same as it's components

    _a = a; _b = b; _coeff_b = coeff;
    Unit.registerUnit (this);
    register_mult_constraint (this, a, b, coeff);
}

/** registers a new unit in the UnitCollection 
 * of the form u = a * b^coeff*/
public static void register_mult_constraint (Unit u, Unit a, Unit b, Fraction coeff)
{
    if ((a != null) && (b != null))
    {
        if (Analyze.doCompoundConstraints)
            _current_unit_collection.add_constraint (u, a, b, coeff);
//        util.ASSERT (a.get_type() == b.get_type());
        // util.ASSERT ((coeff == 1) || (coeff == -1));
    }
}

public Unit get_unit_a() { return _a; }
public Unit get_unit_b() { return _b; }
public Fraction get_coeff() { return _coeff_b; }

private static final Fraction half = new Fraction(1,2);
private static final Fraction minus_one = new Fraction(-1,1);
private static final Fraction one = new Fraction(1,1);

public String toString ()
{
    boolean is_mult = (_coeff_b.equals(one));
    boolean is_div = (_coeff_b.equals(minus_one));
    boolean is_sqrt = (_a == _b) && (_coeff_b.equals(half));

    String op = "???";
    if (is_mult) op = "Product";
    else if (is_div) op = "Divide";
    else if (is_sqrt) op = "Sqrt ";
    else op = "Power (" + _coeff_b + ")";


    StringBuffer sb = new StringBuffer();
    if (is_mult || is_div)
    {
        sb.append("{" + op + " of ");
        if (_a != null)
            sb.append (_a.short_toString());
        else
            sb.append ("null");
        String str = (is_mult ? " * " : " / ");
        sb.append (str);
        if (_b != null)
            sb.append (_b.short_toString());
        else
            sb.append ("null");
    }
    else if (is_sqrt) 
    { 
        Util.ASSERT (_a == _b); 
        sb.append ("Sqrt of ");  
        if (_b != null)
            sb.append (_b.short_toString());
        else
            sb.append ("null");
    }
    else
    {
        sb.append("{ Product of ");
        if (_a != null)
            sb.append (_a.short_toString());
        else
            sb.append ("null");

        sb.append (" * ");
        if (_b != null)
            sb.append (_b.short_toString());
        else
            sb.append ("null");
        sb.append (" ^ " + _coeff_b);
    }

    sb.append ("}");
    return sb.toString();

}

}
