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

package unifi;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import unifi.units.FieldUnit;
import unifi.units.Unit;
import unifi.util.Util;

/** this class implements user-identifiable names. Note: this captures a *specification*
 * of names, as provided by a user. It does not reflect the names of units after analysis. 
 * Many more units may have names associated with them after analysis and unification. */
public class DisplayNames implements Serializable {
	
public static Logger parent_logger = Logger.getLogger("unifi");
private static Logger logger = Logger.getLogger("unifi.DisplayNames");

/** the structure allows multiple names per unit, even though we dont expect users will use it that way */
private Map<Unit, Set<String>> namesMap = new LinkedHashMap<Unit, Set<String>>();
	
public void clear()
{
	namesMap.clear();
}

private void parseLine(String l)
{
	StringTokenizer st = new StringTokenizer(l);
	while (st.hasMoreTokens())
	{
		String lineType = st.nextToken();
		if ("F".equals(lineType))
		{
			String fieldName = st.nextToken();
			fieldName = fieldName.trim(); // just to be safe
			FieldUnit fu = FieldUnit.lookup(fieldName);
			if (fu == null)
				logger.warning ("No such fieldname: " + fieldName);
			else
			{
				String name = st.nextToken();
				addName(fu, name);
				logger.warning ("Field found " + fu + " assigning name = " + name);
			}
		}
	}
}

public void parseFile(String filename) throws IOException
{
	BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
	while (true)
	{
		String line = r.readLine();
		if (line == null)
			break;
		line = line.trim();
		if ("".equals(line) || line.startsWith("#")) // ignore comment and blank lines
			continue;
		parseLine(line);
	}
	
	System.out.println (toString());
}

public void save(String filename) throws IOException
{
	ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
	oos.writeObject(namesMap);
	oos.close();
}

public void restore(String filename) throws IOException, ClassNotFoundException
{
	ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename));
	namesMap = (Map<Unit,Set<String>>) ois.readObject();	
	ois.close();
}

/** returns all the names for this unit */
public Set<String> getNames(Unit u)
{
	return namesMap.get(u);
}

public void setNames(Unit u, Set<String> set)
{
	namesMap.put(u, set);
}

/** returns a single name, arbitrarily picking up one if more than one name for this unit. returns null if no names */
public String getName(Unit u)
{
	Set<String> set = getNames(u);
	if (set == null)
		return null;

	// return the first element returned by the iterator
	for (String name: set)
		return name;

	Util.ASSERT (false); // should not reach here - if set created, it has at least one element
	return null; 	
}

public void addName(Unit u, String name)
{
	Set<String> set = namesMap.get(u);
	if (set == null)
	{
		set = new LinkedHashSet<String>();
		namesMap.put (u, set);
	}
	set.add(name);
}

public String toString()
{
	StringBuilder sb = new StringBuilder(); 
	String delim = "::"; // arbitrary delimiter between unit toString and each names.  
	for (Map.Entry<Unit,Set<String>> me : namesMap.entrySet())
	{
		Unit u = me.getKey();
		Set<String> list = me.getValue();
		Util.ASSERT (u != null);
		
		sb.append (u.toString() + delim); // we may choose something more reliable than u.toString() later
 		for (String name : list)
			sb.append (name + delim);
		sb.append ("\n");
	}
	return sb.toString();
}

/** simple test */
public static void main (String args[]) throws IOException
{
	DisplayNames n = new DisplayNames();
	n.parseFile ("test.names");
	System.out.println (n);
}

}
