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

import org.apache.bcel.generic.*;

import unifi.BCP;

import java.util.*;

/**
 * <p>Title: </p>
 * <p>Description: Array Length Unit</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class CheckcastUnit extends Unit
{
BCP _bcp;
private static HashMap<String, CheckcastUnit> globalCheckcastUnitDir = new HashMap<String, CheckcastUnit>();

private CheckcastUnit(Type t, BCP bcp)
{
    super (t);
    _bcp = bcp;
}

public static CheckcastUnit get_checkcast_unit(Type t, BCP bcp)
{
    CheckcastUnit u = globalCheckcastUnitDir.get(bcp.toString());
    if (u == null)
    {
        u = new CheckcastUnit(t, bcp);
        globalCheckcastUnitDir.put (bcp.toString(), u);
        Unit.registerUnit(u);
    }
    return u;
}

public String toString ()
{
    return "Cast at " + _bcp + " type " + type;
}

public int hashCode() { return _bcp.hashCode(); }
public boolean equals(Object o)
{
    if (!(o instanceof CheckcastUnit))
        return false;

    return _bcp.equals (((CheckcastUnit) o)._bcp);
}

}

