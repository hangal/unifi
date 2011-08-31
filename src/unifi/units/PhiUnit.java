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


import java.util.*;
import org.apache.bcel.generic.*;

import unifi.BCP;

/** a dummy "phi" unit which is sometimes needed at meet operators */
public class PhiUnit extends Unit {

BCP bcp;
int stackSlot; // stack position at this bcp that this phi unit is representing
private static HashMap<String, PhiUnit> globalPhiUnitDir = new LinkedHashMap<String, PhiUnit>();

private PhiUnit(BCP b, int slot, Type t)
{
    super(t);
    this.bcp = b;
    this.stackSlot = slot;
}

// just a canonical representation of the bcp used to index into the directory
private static String getBCPKey(BCP bcp)
{
	return bcp.get_class_name() + "." + bcp.get_method_name_and_sig() + "-P" + bcp.get_pos() + "-L" + bcp.get_src_line();
}

public static PhiUnit get_phi_unit(BCP bcp, int slot, Type t)
{
    String key_str = getBCPKey(bcp) + "-S" + slot;

    PhiUnit p = globalPhiUnitDir.get(key_str);
    if (p == null)
    {
        p = new PhiUnit(bcp, slot, t);
        globalPhiUnitDir.put(key_str, p);
        Unit.registerUnit(p);
    }
    return p;
}

public String toString()
{
    return "Merge at " + this.bcp.toString() + ", slot " + stackSlot + ", type " + type;
}

public int hashCode ()
{
    return bcp.hashCode();
}

public boolean equals (Object o)
{
    if (!(o instanceof PhiUnit))
        return false;

    PhiUnit p = (PhiUnit) o;
    return ((stackSlot == p.stackSlot) && bcp.equals(p.bcp));
}

}
