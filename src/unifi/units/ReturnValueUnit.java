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

import unifi.util.Util;

public class ReturnValueUnit extends Unit implements Serializable, Cloneable
{
	public static final long serialVersionUID = 1L;

private String _full_sig;
boolean _is_public_or_protected;
private boolean _is_in_analyzed_code;
private int cloneNum = -1;

public boolean is_in_analyzed_code() { return _is_in_analyzed_code; }
public void set_is_in_analyzed_code(boolean b) { _is_in_analyzed_code = b; }

public String get_full_sig () { return _full_sig; }

public ReturnValueUnit (String full_sig, Type t, boolean is_public_or_protected, boolean is_in_analyzed_code)
{
    super (t);
    _full_sig = full_sig.intern();
    _is_public_or_protected = is_public_or_protected;
    _is_in_analyzed_code = is_in_analyzed_code;
}

public void setCloneNum (int c) { cloneNum = c; }
public int getCloneNum() { return cloneNum; }
public boolean isClone() { return cloneNum >= 0; }

public boolean isPublicOrProtected() { return _is_public_or_protected; }

/*
public String short_toString ()
{
    return "RV " + util.strip_package_from_method_sig (_full_sig);
}
*/

public String toShortString ()
{
	// WARNING: the toString here is slightly fragile since it is used verbatim in the watchlist for matching
	// do not change the structure without checking watchlist code

    return "RV of" +  ((cloneNum >= 0) ? (" Call#" + cloneNum) : "") + " "
    	 + Util.strip_package_from_method_sig (_full_sig)
         + ", type " + type + (isTypeForced ? " (forced)":"")
         + (_is_in_analyzed_code ? "" : " [in library] ");
}

public String toVeryShortString()
{
    return Util.strip_package_from_method_sig (_full_sig)
    		+ ((cloneNum >= 0) ? (" Call#" + cloneNum) : "");
}

public String full_toString()
{
	return toString();
}

public String toString ()
{
    return "RV of" +  ((cloneNum >= 0) ? (" Call#" + cloneNum) : "") + " "
	 + (_full_sig)
    + ", type " + type + (isTypeForced ? " (forced)":"")
    + (_is_in_analyzed_code ? "" : " [in library] ");
}

public int hashCode ()
{
    return _full_sig.hashCode() ^ cloneNum;
}

public boolean equals (Object o)
{
    if (!(o instanceof ReturnValueUnit))
	return false;

    ReturnValueUnit other = (ReturnValueUnit) o;

    return (_full_sig.equals (other._full_sig) &&
//	   type.toString().equals (other.type.toString()) &&
	   cloneNum == other.cloneNum);
}

}
