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

package unifi.drivers;

import java.util.regex.*;
import java.io.*;
import java.util.*;

/** selects class and method names for analysis
// will read config from a file pointed to by -Dunifi.control.file
// with entries of the form
// + a.b.c
// - x.y.*
// etc. and return whether or not a given name is selected based
// on the first matching regexp in this file.
// by default, returns true if there is no match.
*/

public class NameFilter {

private List<Pattern> name_patterns = new ArrayList<Pattern>();
private List<Boolean> is_positive = new ArrayList<Boolean>();

public NameFilter() throws IOException
{
    String filename = System.getProperty("unifi.control.file");
    if (filename != null)
    {
        LineNumberReader l = new LineNumberReader(new FileReader(filename));
        String s;
        while ((s = l.readLine()) != null)
        {
        	s = s.trim();
        	// ignore blank and # lines
        	if (s.startsWith ("#"))
        		continue;
            StringTokenizer st = new StringTokenizer (s, " \t");
            if (st.countTokens() == 0)
            	continue;
            String plus_or_minus = st.nextToken();
            String str = st.nextToken();
            Pattern p = Pattern.compile (str, 0);
            name_patterns.add (p);
            boolean is_neg = plus_or_minus.equals ("-");
            is_positive.add (Boolean.valueOf(!is_neg));
        }
    }

    // always remove java.* and hashcode methods
    // need double escape below so that the RE compiler will see a single escape
//    name_patterns.add (Pattern.compile("java\\..*", 0)); is_positive.add (Boolean.valueOf(false));
    // remove toString methods
    // we ignore body of toString methods because sometimes they return a string field
    // which unifies that field with return value of j.l.Object.toString()
    name_patterns.add (Pattern.compile(".*\\.hashCode\\(.*", 0));
    name_patterns.add (Pattern.compile(".*\\.toString\\(.*", 0));
    name_patterns.add (Pattern.compile(".*\\.compare\\(.*", 0));
    name_patterns.add (Pattern.compile(".*\\.compareTo\\(.*", 0));

    // all -ves
    for (Pattern p: name_patterns)
    	is_positive.add (Boolean.valueOf(false));
}

/**
 * returns whether or not the given name is selected
 */
public boolean select (String name)
{
    int size = name_patterns.size();
    for (int i = 0; i < size; i++)
    {
        // System.out.println ("testing match for name = " + name + " with pattern " + name_patterns.get(i));
        if (name_patterns.get(i).matcher(name).matches())
        {
            // System.out.println ("matches for name = " + name);
            // System.out.println ("result = " + is_positive.get(i));
            return is_positive.get(i).booleanValue();
        }
    }
    return true; // by default
}

public String toString()
{
    StringBuffer sb = new StringBuffer();
    int size = name_patterns.size();
    for (int i = 0; i < size; i++)
    {
        sb.append ("" + i + ". " + (is_positive.get(i).booleanValue() ? "+" : "-"));
        sb.append (" ");
        sb.append (name_patterns.get(i));
        sb.append ("\n");
    }
    return sb.toString();
}

}

