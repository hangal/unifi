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

import unifi.solver.*;
import unifi.units.Unit;
import unifi.util.Util;

public class FormulaDiffRecord extends DiffRecord {

private Unit u;
private Constraint c1, c2;
private Dimension d1, d2;

public FormulaDiffRecord(Unit u, Constraint c1, Constraint c2, Dimension d1, Dimension d2)
{
    this.u = u;
    this.c1 = c1;
    this.c2 = c2;
    this.d1 = d1;
    this.d2 = d2;
}

public int compareTo (Object o)
{
    if (o instanceof UnitDiffRecord)
        return 1;
    if (o instanceof DimensionLessUnitDiffRecord) 
        return 1;

    Util.ASSERT (o instanceof FormulaDiffRecord);
    FormulaDiffRecord other = (FormulaDiffRecord) o;

    // arbitrary but consistent ordering
    return toString().compareTo(other.toString());
}

public String toString()
{
    StringBuilder sb = new StringBuilder();
    sb.append ("Unit diff record MISMATCH: differences between formula for unit " + u + "\n");
    sb.append ("Constraint in UC1: " + c1 + "\n");
    sb.append ("Constraint in UC2: " + c2 + "\n");
    sb.append ("d1 : " + d1 + "\n");
    sb.append ("d2 : " + d2 + "\n");
    sb.append ("d1.exponent : " + d1.exponent() + "\n");
    sb.append ("d2.exponent : " + d2.exponent() + "\n");
    sb.append ("exponent matches = " + d1.exponent().equals(d2.exponent()) + "\n");
    sb.append ("\n");
    return sb.toString();
}

}
