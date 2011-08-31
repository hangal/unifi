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
import java.io.*;
import org.apache.bcel.generic.*;
import unifi.BCP;
import unifi.util.Util;

/** allocation site unit.
TODO: have a static factory method instead of making global dir public.
holding bcp might be enough - it already contains class name etc.
*/

public class AllocUnit extends Unit implements Serializable
{
    private String _method_name;
    private BCP _bytecode_pos;
    public static HashMap<BCP, AllocUnit> globalAllocUnitDir = new LinkedHashMap<BCP, AllocUnit>();

    // there are multiple alloc units at an allocation of an array
    // level helps to distinguish between those units
    // the base unit of an array (or at a plain new) have a level of -1
    // successive array units have levels from 0 onwards
    private int level;

public AllocUnit (Type t, int level, String m, BCP bcp)
{
    super (t);
    _method_name = m;
    _bytecode_pos = bcp;
    this.connectedToSingleUnit = true;
    this.level = level;
}

public String toString ()
{
    return Util.strip_package_from_class_name(getType().toString()) + " type object allocated at " + _bytecode_pos
           + " (in method " + _method_name + ")";
}

public int hashCode ()
{
    return _method_name.hashCode() + _bytecode_pos.hashCode() + level; // getType().toString().hashCode()
}

public boolean equals (Object o)
{
    if (!(o instanceof AllocUnit))
	return false;

    AllocUnit other = (AllocUnit) o;

    return (_method_name.equals (other._method_name)
	   && _bytecode_pos.equals (other._bytecode_pos)
	   && level == other.level);
//	   getType().toString().equals (other.getType().toString());
}

}

