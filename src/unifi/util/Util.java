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

package unifi.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.*;

import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;

public class Util
{

private static Logger _logger = Logger.getLogger("unifi.util");

public static void fatal (String s)
{
	die (s);
}

public static void fatal (Exception e)
{
	die("Exception: " + e);
}

public static void fatal (String mesg, Exception e)
{
	die (mesg + "\nException: " + e);
}

public static void warn (String s)
{
    System.err.println ("WARNING: " + s);
    //System.exit (32);
}

public static void ASSERT (boolean b)
{
    if (!b)
    {
    	String s = "Assertion Failed! Stack trace:\n" + stackTrace();
    	_logger.severe(s);
        System.err.println (s);
        throw new RuntimeException(s);
    }
}

public static void ASSERT (boolean b, String message)
{
    if (!b)
    {
    	String s = "Assertion Failed!\n" + message + "\n";
    	_logger.severe(s);
        System.err.println (s);
        throw new RuntimeException(s);
    }
}

public static void die ()
{
	ASSERT (false);
}

public static void die (String message)
{
	ASSERT (false, message);
}

/* convenience methods to print stack trace at current point...
 * would have been wonderful if we could examine the stack and
 * print all the variables in scope...
 */
public static String stackTrace(Throwable t)
{
	StringWriter sw = new StringWriter(0);
	PrintWriter pw = new PrintWriter(sw);
	t.printStackTrace(pw);
	pw.close();
	return sw.getBuffer().toString();
}

public static String stackTrace ()
{
    Throwable t = new Exception("Printing current stack trace");
    t.fillInStackTrace();
    return stackTrace(t);
}

public static String getMemoryStats()
{
	Runtime r = Runtime.getRuntime();
	System.gc();
	int MB = 1024 * 1024;
	return r.freeMemory()/MB + " MB free, " + (r.totalMemory()/MB - r.freeMemory()/MB) + " MB used, "+ r.maxMemory()/MB + " MB max, " + r.totalMemory()/MB + " MB total";
}

// converts fq class names to simple names
// e.g. a.b.c.d to d
public static String strip_package_from_class_name (String class_name)
{
    // System.out.print ("input is " + s);

    int z = class_name.lastIndexOf ('.');
    if (z >= 0)
        class_name = class_name.substring (z+1);
    else
    {
        z = class_name.lastIndexOf ('/');
        if (z >= 0)
            class_name = class_name.substring (z+1);
    }

    return class_name;
}

public static Set<String> readStreamAndInternStrings(String filename) throws IOException
{
	Reader r = new InputStreamReader (new FileInputStream(filename), "UTF-8");
	Set<String> result = new LinkedHashSet<String>();
	LineNumberReader lnr = new LineNumberReader(r);
	while (true)
	{
		String word = lnr.readLine();
		if (word == null)
		{
			lnr.close();
			break;
		}
		word = word.trim();
		if (word.startsWith("#") || word.length() == 0)
			continue;
		result.add(word);
	}
	return result;
}

public static String shorten_package_for_class_name(String class_name)
{
	// extract package/class name along path in class_name, delimited by slash-or-dot
	StringTokenizer st = new StringTokenizer(class_name, "/.");
	List<String> list = new ArrayList<String>();
	while (st.hasMoreTokens())
		list.add(st.nextToken());

	StringBuilder result = new StringBuilder();
	for (int i = 0; i < list.size()-1; i++)
		result.append(list.get(i).substring(0,1) + "."); // first letter of package + the dot

	result.append(list.get(list.size()-1));

	return result.toString();
}

// converts fq method names to simple names
// e.g. a.b.c.d to c.d
// a.b.c.d(somesig)V to c.d(somesig)V
public static String strip_package_from_method_sig (String s)
{
    // System.out.print ("input is " + s);

    String sig = "";
    String full_name = s;

    // first split based on whether there is a sig component in s
    int x = s.indexOf ('(');
    if (x >= 0)
    {
        full_name = s.substring (0, x);
        sig = s.substring (x);
    }

    int y = full_name.lastIndexOf ('.'); // y shd be > 0
    String class_name = full_name.substring(0, y);

    int z = class_name.lastIndexOf ('.');
    if (z >= 0)
        full_name = full_name.substring (z+1);
    else
    {
        z = class_name.lastIndexOf ('/');
        if (z >= 0)
            full_name = full_name.substring (z+1);
    }

    StringBuffer sb = new StringBuffer(full_name);
    sb.append ("(");

    Type t[] = Type.getArgumentTypes(sig);
    for (int i = 0; i < t.length; i++)
    {
        if (t[i] instanceof ObjectType)
            sb.append (strip_package_from_class_name (t[i].toString()));
        else
            sb.append (t[i]);

        if (i != (t.length-1))
            sb.append (",");
    }
    sb.append (")");

    return sb.toString();
}

// converts fq field names to simple names
// e.g. a.b.c.d to c.d
public static String strip_package_from_field_name (String s)
{
    String full_name = s;

    int y = full_name.lastIndexOf ('.'); // y shd be > 0
    String field_name_with_dot = full_name.substring(y);
    String class_name = full_name.substring(0, y);

    return strip_package_from_class_name(class_name) + field_name_with_dot;
}

/** converts fq field names to shorter, understandable names
 */
public static String shorten_package_for_field_name (String s)
{
	String full_name = s;

	int y = full_name.lastIndexOf ('.'); // y shd be > 0
	String field_name_with_dot = full_name.substring(y);
	String class_name = full_name.substring(0, y);

	return shorten_package_for_class_name(class_name) + field_name_with_dot;
}

// maps method sigs to classes they belong to
// e.g. a.b.m to a.b
// a.b.m(somesig)V to m(somesig)V
public static String delete_after_last_dot (String s)
{
    int x = s.lastIndexOf ('.');
    if (x >= 0)
        s = s.substring (0,x);

    return s;
}

// maps method sigs to classes they belong to
// e.g. a.b.m to m
// a.b.m(somesig)V to m(somesig)V
public static String delete_to_last_dot (String s)
{
    int x = s.lastIndexOf ('.');
    if (x >= 0)
        s = s.substring (x+1);

    return s;
}

/** looks up repository for class_name and returns corresponding
 * JavaClass object. returns null if class is not found
 */
public static JavaClass get_JavaClass(String class_name)
{
	JavaClass clazz = null;
	try
	{
		clazz = Repository.lookupClass (class_name);
	}catch(Exception cnfe) // Should have been ClassNotFoundException
	{
		System.err.println("WARNING: Repository failed to find class " + class_name);
	}

	return clazz;
}

public static boolean nullOrEmpty (String x)
{
	return (x == null || "".equals(x));
}

public static void breakpoint()
{
	breakpoint("");
}

public static void breakpoint(String s)
{
	_logger.severe("----------------------\nBREAKPOINT! " + s + "\n" + Util.stackTrace() + "\n-----------------------------\n");
}

}