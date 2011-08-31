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


import java.io.*;
import java.util.*;

import org.apache.bcel.generic.*;

import unifi.BCP;
import unifi.util.Util;

public class ConstantUnit extends Unit implements Serializable
{
BCP _bcp;
Object constVal; // constVal is an object from BCEL, we don't really know what it is.
private static HashMap<BCP, ConstantUnit> globalConstantUnitDir = new LinkedHashMap<BCP, ConstantUnit>();

/* full name has to be of the form class.field */
private ConstantUnit (Type t, BCP bcp, Object val)
{
    super (t);
    _bcp = bcp;
    constVal = val;
}

public static ConstantUnit get_constant_unit (Type t, BCP bcp, Object val)
{
    ConstantUnit u = globalConstantUnitDir.get(bcp);
    if (u == null)
    {
        u = new ConstantUnit (t, bcp, val);
        globalConstantUnitDir.put (bcp, u);
        Unit.registerUnit (u);
    }

    return u;
}

public Object val() { return constVal; }

/** equality of constant units depends on them being exact same bcp
 * as well as BCEL value object.equals().
 */
public boolean equals (Object o)
{
    if (!(o instanceof ConstantUnit))
	return false;
    ConstantUnit other = (ConstantUnit) o;

    return (this._bcp.equals (other._bcp) &&
            this.constVal.equals (other.constVal));
}

@Override
public int hashCode()
{
    return (_bcp.hashCode() ^ constVal.hashCode());
}

public String toString ()
{
    if (isStringType())
    {
        // replace new lines if any in the const. string with \n because newlines during printing the const value
        // screw up formatting
    	// FLAG DEBUG
    	/*
    	if (!(constVal instanceof String))
    		Util.breakpoint();
    	 */
        String s = constVal.toString();
        s.replace ("\n", "\\n");
        return "Constant \"" + s + "\" in " + _bcp + ", type " + type;
    }
    else
        return "Constant " + constVal + " in " + _bcp + ", type " + type;
}
}
