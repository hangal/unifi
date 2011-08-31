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
import org.apache.bcel.generic.*;

import unifi.BCP;
import unifi.util.Util;

/** This class represents the bucket elements which are of type local
 * variables. The local variables are put into Unitelement object and
 * according to their unit are placed in the right Unit */

public class LocalVarUnit extends Unit implements Serializable
{
    String _method_descriptor, _varname;
    boolean _certain = false; // if !_certain, then type and varname are best guesses, not guaranteed to be correct
    /** The index of the local var in the local var table */
    int _logical_index;
    int _phys_index;
    BCP _bcp;

    public LocalVarUnit (MethodGen mg, ConstantPoolGen cpgen, int logical_index, int physical_index,
                         int pos, String varname, Type t, boolean certain)
    {
        // type may be null
        super (t);
        _method_descriptor = (mg.getClassName () + "." + mg.getName () +  mg.getSignature ()).intern();
        _logical_index = logical_index;
        _phys_index = physical_index;
        _varname = varname;
        _bcp = new BCP (mg, cpgen, pos);
        _certain = certain;
    }

    public String short_toString ()
    {
        return "LV " + _varname + " in method " + Util.strip_package_from_method_sig(_method_descriptor);
    }

    @Override
    public String toVeryShortString ()
    {
    	String s =  Util.strip_package_from_method_sig(_method_descriptor);
    	int idx = s.indexOf("(");
    	if (idx >= 0)
    		s = s.substring (0, idx);
        return _varname + " in " + s;        
    }

    public String toString ()
    {
        return "LV " + _varname + " in method " + Util.strip_package_from_method_sig(_method_descriptor) + ", type " + type
	                + " (physical # " + _phys_index
                        + " logical #" + _logical_index + ", " + _bcp + ")";
    }

    public int hashCode()
    {
        return _method_descriptor.hashCode() ^ _logical_index;
    }

    public boolean equals(Object o)
    {
        if (!(o instanceof LocalVarUnit))
            return false;

        LocalVarUnit other = (LocalVarUnit) o;

        if (_varname != null)
            if (!_varname.equals(other._varname))
                return false;

        return this._method_descriptor.equals(other._method_descriptor) && (this._logical_index == other._logical_index) && this._bcp.equals(other._bcp);
        // all these will be equal if the method is mostly unchanged.
        // if the method has changed even slightly, the local vars may not get associated.
        // do we want to revisit this ??
        // return this._varname.equals(other._varname) && this._method_descriptor.equals(other._method_descriptor) &&this._bcp == other._bcp && (this._phys_index == other._phys_index) && (this._logical_index == other._logical_index);
    }

	public boolean isCertain() {
		return _certain;
	}

}
