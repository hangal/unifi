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
import java.io.*;
import unifi.*;
import unifi.units.Unit;

/** 
 * a Constraint is a shell object which mainly contains 
 * a dimensions object inside it. it just says that 
 * that the product of the dimensions in the dimensions
 * object is dimensionless.
 */
public class Constraint implements Serializable {

private Dimensions _d;

/** is_formula for a constraint means it's the official
    definition of some unit
*/
boolean _is_formula = false;

/**
 * create the constraint u = u1 * u2^coeff, 
 * i.e. u^-1 * u1 * u2^coeff is dimensionless
 */
public Constraint (Unit u, Unit u1, Unit u2, int coeff)
{
    _d = new Dimensions (u1, u2, coeff);
    _d.merge_dim (u, new Fraction(-1));
    System.out.println (_d);
}

public Constraint (Unit u, Dimensions d) 
{ 
    _d = (Dimensions) d.clone(); 
    _d.merge_dim (u, new Fraction(-1));
}

public void flatten()
{
    _d.flatten();
}

public Dimensions dimensions () { return _d; }
public void set_is_formula (boolean b) { _is_formula = b; }
public boolean is_formula () { return _is_formula; }

public String toString()
{
    return ("The following is dimensionless:\n" + _d.neat_toString());
}

public int hashCode () { return _d.hashCode(); }

/** warning - this doesn't really do anything interesting, because
 * Dimensions doesn't have an equals method.
 */
public boolean equals (Object o)
{
//    if (!o instanceof Constraint)
//        return false;

    Constraint c = (Constraint) o;
    return (_d.equals (c._d));
}

public void verify()
{
    Dimensions d = _d;
    d.verify();
}

}

