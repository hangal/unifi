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
import java.util.logging.*;
import org.apache.bcel.generic.*;

import unifi.util.Util;

public class MethodParamUnit extends Unit implements Serializable, Cloneable
{
	public static final long serialVersionUID = 1L;

private static Logger _logger = Logger.getLogger("unifi.MethodParamUnit");

private int _index;
private String _full_sig, _arg_name;
private boolean isPublicOrProtected;
private boolean _is_in_analyzed_code;
private int cloneNum = -1;

public String get_full_sig () { return _full_sig; }
public int get_index () { return _index; }

public boolean is_in_analyzed_code() { return _is_in_analyzed_code; }
public void set_is_in_analyzed_code(boolean b) { _is_in_analyzed_code = b; }

/* n is the position in the arg list of the method.
   numbering starts from 0, with the this pointer, if there is one
   mg can be null - only impact is local var name is not available
 */
public MethodParamUnit (Type t, String full_method_sig, MethodGen mg, int n, boolean is_public_or_protected, boolean is_in_analyzed_code)
{
    super (t);
    _index = n;
    _full_sig = full_method_sig.intern();
    _is_in_analyzed_code = is_in_analyzed_code;

    if (mg != null)
    {
        LocalVariableGen[] lvgen = mg.getLocalVariables();
        for (int i = 0; i < lvgen.length; i++)
            if (lvgen[i].getIndex() == _index)
            {
                _arg_name = lvgen[i].getName().intern();
            }
    }

    if (_arg_name == null)
        _arg_name = "arg" + _index;

    isPublicOrProtected = is_public_or_protected;
}

public void setCloneNum(int c) { cloneNum = c; }
public int getCloneNum() { return cloneNum; }
public boolean isClone() { return cloneNum >= 0; }

public boolean isPublicOrProtected() { return isPublicOrProtected; }

// given a local var index for a param, return the param name
// if local var info is not available, we just return "arg<idx>"
// one would think that methodgen.getArgumentName(index) would
// do the same thing, but surprisingly, it doesn't - it just
// returns "arg0", "arg1", etc. - sgh
// public String getArgName(int idx)
// {
// }

public String short_toString ()
{
	// WARNING: the toString here is slightly fragile since it is used verbatim in the watchlist for matching
	// do not change the structure without checking watchlist code

	StringBuffer sb = new StringBuffer();
    if (isClone())
    	sb.append ("Call# " + cloneNum + " ");
    sb.append ( "Param " + _arg_name +
    			" of method " + Util.strip_package_from_method_sig(_full_sig) +
    			", type " + type + (isTypeForced ? " (forced)":""));
    sb.append (_is_in_analyzed_code ? "" : " [in library] ");
    appendAttrString (sb);
    return sb.toString();
}

public String full_toString()
{
	return toString();
}

public String toString ()
{
	StringBuffer sb = new StringBuffer();
	if (isClone())
		sb.append ("Call# " + cloneNum + " ");

    sb.append ("Param " + _arg_name + " of method " + _full_sig + ", type " + type  + (isTypeForced ? " (forced)":""));
    sb.append (_is_in_analyzed_code ? "" : " [in library] ");
    appendAttrString (sb);
    return sb.toString();
}

public int hashCode ()
{
    return _arg_name.hashCode() ^ _full_sig.hashCode() ^ cloneNum;
}

public boolean equals (Object o)
{
    if (!(o instanceof MethodParamUnit))
	return false;

    MethodParamUnit other = (MethodParamUnit) o;

    return (_arg_name.equals (other._arg_name) &&
	    _full_sig.equals (other._full_sig) &&
	//    type.toString().equals (other.type.toString()) &&
	    (cloneNum == other.cloneNum)
	    );
}


}
