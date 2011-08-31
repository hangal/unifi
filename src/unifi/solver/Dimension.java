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

import unifi.units.Unit;
import unifi.util.Util;

/** a dimension is a unit with an exponent
 */
public class Dimension implements Cloneable, Serializable {
Unit _u;
Fraction _exponent;
public Dimension (Unit u, int coeff) { _u = u; _exponent = new Fraction(coeff); }
public Dimension (Unit u, Fraction exp) { _u = u; _exponent = exp; }
public boolean equals (Object o)
{
    Dimension d = (Dimension) o;
    return _u.equals (d._u);
}

public Unit unit() { return _u; }
public Fraction exponent() { return _exponent; }
public void set_exponent(Fraction e) { _exponent = e; }
public Object clone()
{
    try { Dimension d = (Dimension) super.clone(); d._exponent = (Fraction) _exponent.clone(); return d;}
    catch (CloneNotSupportedException cnse)
    { Util.ASSERT (false); return null;}
}

public String toString()
{
    return (_u + "^" + _exponent);
}

public String short_toString()
{
    return (_u.short_toString() + "^" + _exponent);
}

public String reverse_short_toString()
{
    return (_exponent + "^" + _u.short_toString());
}
}
