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

import unifi.units.Unit;
import unifi.util.Util;

public class DimensionLessUnitDiffRecord extends DiffRecord {

private Unit u1, u2;
boolean is_u1_dimension_less, is_u2_dimension_less;

public DimensionLessUnitDiffRecord(Unit u1, Unit u2, boolean is_u1_dimension_less, boolean is_u2_dimension_less)
{
    this.u1 = u1;
    this.u2 = u2;
    this.is_u1_dimension_less = is_u1_dimension_less;
    this.is_u2_dimension_less = is_u2_dimension_less;
}

public int compareTo (Object o)
{
    if (o instanceof UnitDiffRecord)
        return 1;
    if (o instanceof FormulaDiffRecord) 
        return -1;

    Util.ASSERT (o instanceof DimensionLessUnitDiffRecord);
    DimensionLessUnitDiffRecord other = (DimensionLessUnitDiffRecord) o;

    // arbitrary but consistent ordering
    return toString().compareTo(other.toString());
}

public String toString()
{
    StringBuilder sb = new StringBuilder();
    sb.append (u1 + " is " + (is_u1_dimension_less ? "" : "not ")
                    + "dimensionless, while equivalent unit " 
                    + u2 + " is " + (is_u2_dimension_less ? "" : "not "));
    return sb.toString();
}

}
