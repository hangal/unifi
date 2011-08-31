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


import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.bcel.generic.Type;

import unifi.UnitCollection;
import unifi.util.Util;

public class FieldUnit extends Unit implements Serializable
{
	public static final long serialVersionUID = 1L;

public static Logger parentLogger = Logger.getLogger("unifi");
private static Logger logger = Logger.getLogger("unifi.FieldUnit");
private boolean inAnalyzedCode = false;
private boolean used = false; // true iff this field has actually been used in the target program. fieldunits are also created when we begin to analyze a class.
private static Set<String> ignoreList; // these fields will be ignored, e.g. cos they are polymorphic
static {
	readIgnoreList();
}

private String _field_full_name;
private boolean isPrivate, isProtected, isPublic;
private boolean isStatic;
public static Map<String,FieldUnit> globalFieldUnitDir = new LinkedHashMap<String,FieldUnit> ();

/* full name has to be of the form class.field */
private FieldUnit (Type t, String full_name, boolean is_public, boolean is_protected, boolean is_private, boolean is_static)
{
    super (t);
    this.isPrivate = is_private;
    this.isStatic = is_static;
    this.isProtected = is_protected;
    this.isPublic = is_public;

    if (logger.isLoggable(Level.FINE))
    	logger.fine ("Creating new FieldUnit for: " + full_name + " type " + t);
    _field_full_name = full_name.intern();
    this.connectedToSingleUnit = true;
}
public boolean is_private() {return isPrivate;}
public boolean is_static() { return isStatic; }
public String full_name() { return _field_full_name; }

private static void readIgnoreList()
{
	String filename = System.getProperty("ignore.fields");
	if (filename == null)
		return;
	try {
		ignoreList = Util.readStreamAndInternStrings(filename);
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
		logger.severe ("Unable to read ignore.fields file: " + filename + "\n" + e.toString());
	}
}

public void setInAnalyzedCode(boolean inAnalyzedCode) {
	this.inAnalyzedCode = inAnalyzedCode;
}
public boolean isInAnalyzedCode() {
	return inAnalyzedCode;
}
/** used for simple lookups, no allocation. may consider adding static flag.
 * is idempotent, can be called from anywhere */
public static FieldUnit lookup(String full_name)
{
	return FieldUnit.globalFieldUnitDir.get(full_name);
}

/** returns a fieldunit with the given name, allocating it if necessary.
 * only the main analysis should be calling this.
 */
public static FieldUnit get_field_unit (Type t, String full_name,  boolean is_public, boolean is_protected, boolean is_private, boolean is_static, boolean real_use)
{
    FieldUnit fue = FieldUnit.globalFieldUnitDir.get (full_name);
    if (fue == null)
    {
    	if (ignoreList != null && ignoreList.contains(full_name))
    	{
    		logger.fine("ignoring " + full_name);
    		return null;
    	}

        fue = new FieldUnit (t, full_name, is_public, is_protected, is_private, is_static);
        Unit.registerUnit (fue);
        globalFieldUnitDir.put (full_name, fue);
    }
    else
    {
        if (!fue.getType().equals (t))
        {
        	Util.die ("Field: " + full_name + " expected type: " + t + " but field already exists with type " + fue.getType());
        }
    }

    if (real_use)
        fue.used = true;
    else
    	fue.setInAnalyzedCode(true); // not a real use, its part of a class that we're analyzing

    return fue;
}

/** removes non-golden units from directory */
public static void keepOnlyGolden()
{
	int count = 0;
	for (Iterator<Map.Entry<String,FieldUnit>> it = globalFieldUnitDir.entrySet().iterator(); it.hasNext(); )
	{
		FieldUnit fu = it.next().getValue();
		if (fu != null && !fu.isGolden())
		{
			count++;
			it.remove();
		}
	}

	logger.info (count + " private field units removed, " + globalFieldUnitDir.size() + " remaining");
}

public static void verifyAllUnitsInUC(UnitCollection uc) {
	for (Iterator<Map.Entry<String,FieldUnit>> it = globalFieldUnitDir.entrySet().iterator(); it.hasNext(); )
	{
		FieldUnit fu = it.next().getValue();
		if (!uc.contains(fu)) {
			Util.die("field unit: " + fu + " not in UC");
		}
	}
}

public int hashCode ()
{
    return _field_full_name.hashCode(); // + type.toString().hashCode();
}

public boolean equals (Object o)
{
    if (!(o instanceof FieldUnit))
	return false;
    FieldUnit other = (FieldUnit) o;

    return (this._field_full_name.equals (other._field_full_name));
    // &&
    //        this.type.toString().equals (other.type.toString()));
}

public String short_toString ()
{
//    StringBuffer sb = new StringBuffer ("Field " + _field_name + ", type " + _type);
    return Util.strip_package_from_field_name(_field_full_name);
}

public String toString ()
{
//    StringBuffer sb = new StringBuffer ("Field " + Util.shorten_package_for_field_name(_field_name) + ", type " + type);
  StringBuffer sb = new StringBuffer ("Field " + _field_full_name + ", type " + type);
    if (isStatic)
        sb.append (" (static)");
    if (isProtected)
    	sb.append (" (protected)");
    else if (isPrivate)
    	sb.append (" (private)");
    if (!used)
        sb.append (" (unused)"); // appended only if its unused, otherwise its assumed to be used.

    appendAttrString (sb);
    return sb.toString();
}

}
