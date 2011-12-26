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


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LineNumberTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;

import unifi.MethodUnits;
import unifi.SavedErrorMessages;
import unifi.Stats;
import unifi.UnitCollection;
import unifi.WatchList;
import unifi.unifi_DF_algorithm;
import unifi.contextsensitive.ContextSensitiveAnalysis;
import unifi.oo.MethodResolver;
import unifi.rd.LogicalLVMap;
import unifi.rd.RD_DF_algorithm;
import unifi.units.FieldUnit;
import unifi.units.Unit;
import unifi.util.MyFormatter;
import unifi.util.Util;
import fieldseekinganalysis.FindPolymorphicMethods;

public class Analyze implements Constants
{

public static boolean CONTEXT_SENSITIVE_ANALYSIS = true;
public static boolean REMOVE_NON_PRIMS = false;

private static boolean _visited = false;
public static boolean doCompoundConstraints = true;
private static final String DEFAULT_LOG_PROPERTIES_FILENAME = "log.properties";
public static Logger _parent_logger = Logger.getLogger("unifi");
static Logger _logger = Logger.getLogger("unifi.drivers.Analyze");

private static final String log_file_name = "unifi.watch.log";
private static PrintStream Out = System.out;
private static PrintStream Log;
private static Thread _gui_thread;
public static NameFilter nameFilter;
private static Stats STATS = new Stats(); // stats object

public static void Tee (String s)
{
//    Out.println (s);
    _logger.info (s);
//   Log.println (s);
}

/* like Tee, but with a newline option */
private static void Tee (String s, boolean newLine)
{
	if (newLine)
		Tee(s);
	else
	{
    	Out.print (s);
    	Log.print (s);
    }
}

static JavaClass get_clazz_from_istream (String name, InputStream is)
{
    JavaClass clazz = null;

    ClassParser cp = new ClassParser (is, name.substring (0, name.length()-".class".length()));
    try {
       clazz  = cp.parse();
    }  catch (ClassFormatError cfe) {
        System.out.println ("Exception while parsing file " + name + "\n" + cfe);
        System.exit(2);
    } catch (IOException ioe) {
        System.out.println ("Error while reading file " + name + "\n" + ioe);
        System.exit(2);
    }

    return clazz;
}

/**
 * gets JavaClass object corresponding to string s.
 * if s is the name of a .class file, then the
 * file is read and the clazz parsed from that.
 * if is is the name of a class (x.y.z), then the
 * file is loaded from the current classpath
 */
private static JavaClass get_clazz (String name) throws IOException
{
    JavaClass clazz = null;
    Tee ("Analyzing class \"" + name + '"');

    if (name.endsWith (".class"))
    {
        InputStream is = new FileInputStream (name);
        clazz = get_clazz_from_istream (name, is);
    }
    else
    {
	try
	{
	    clazz = Repository.lookupClass(name);
	}catch(Exception cnfe) // Should have been ClassNotFoundException
	{
	    Tee("Repository lookup failed for class "+ name );
	}
    }

    if (clazz == null)
    {
        Tee ("\nFATAL ERROR: Unable to locate class \"" + name
             + "\" in current classpath");
        System.exit (2);
    }

    return clazz;
}

/**
 * sets up the logging configuration from the file pointed to by unifi.log.properties.
 * If this property is nto set, just gets it from the resource pointed to by
 * default log properties filename.
 */
public static void setup_logging()
{
    Util.ASSERT (!_visited); // should be called only once
    _visited = true;

	try
	{
	    Log = new PrintStream (new FileOutputStream (log_file_name));
	}
	catch (FileNotFoundException e)
	{
	    Tee ("Unable to open file " + log_file_name);
	    System.exit (2);
	}

    String s1 = System.getProperty ("unifi.log.properties");

    InputStream is = null;
    if (s1 != null)
    {
        try {
            System.out.println ("Reading log properties from " + s1);
            is = new FileInputStream (s1);
        } catch (java.io.IOException ioe)
        {
        	Util.warn ("Unable to read file for logger properties: \"" + s1 + "\"");
        }
    }
    else
    {
        // try to read from the local dir if possible
        try {
        	is = new FileInputStream (DEFAULT_LOG_PROPERTIES_FILENAME);
        	System.out.println("Reading log properties from " + DEFAULT_LOG_PROPERTIES_FILENAME);
        } catch (IOException ioe)
        {
  //          System.out.println ("Unable to read " + DEFAULT_LOG_PROPERTIES_FILENAME);
            // try to read from the unifi jar
            ClassLoader cl = Analyze.class.getClassLoader();
            is = cl.getResourceAsStream(DEFAULT_LOG_PROPERTIES_FILENAME);
        }
    }

    if (is == null)
    {
	    // set up the parent with a default simple formatter on System.err
	    MyFormatter formatter = new MyFormatter();
	    ConsoleHandler consoleHandler = new ConsoleHandler();
	    consoleHandler.setFormatter(formatter);
		_parent_logger.setUseParentHandlers(false); // disable lousy parent formatter
	    _parent_logger.addHandler(consoleHandler);
	    return;
    }

    // "is" is the input stream from where to read the log config.
    try {
        LogManager lm = LogManager.getLogManager();
        lm.readConfiguration (is);
    } catch (IOException ioe)
    {
            Util.warn ("Unable to get read logger configuration file");
            return;
    }
    catch (SecurityException ioe)
    {
        Util.fatal ("Security exception: Unable to set log properties from file \"" + s1 + "\"", ioe);
    }

    /* No need to do all this - its handled by log.properties file
     * http://www.crazysquirrel.com/computing/java/logging.jspx is a good explanation of java logging - sgh, aug 12, 2010
     *
	// the default formatter for the "unifi" logger will be MyFormatter
	// most loggers will default to using this, as their logrecord will be handled by the parent "unifi" logger
	// unless they choose to add their own handler
    MyFormatter formatter = new MyFormatter();
    ConsoleHandler consoleHandler = new ConsoleHandler();
    consoleHandler.setFormatter(formatter);
	_parent_logger.setUseParentHandlers(false); // disable lousy parent formatter
    _parent_logger.addHandler(consoleHandler);
	try {
		FileHandler fileHandler = new FileHandler("unifi.log");
		fileHandler.setFormatter(formatter);
	    _parent_logger.addHandler(fileHandler);
	} catch (Exception e) {
	    System.err.println ("Unable to write to log file unifi.log in current directory: " + System.getProperty("current.dir"));
		e.printStackTrace();
	}
	*/
}

private static void print_usage_and_exit()
{
	System.out.println ("java unifi.drivers.Analyze <options> foo.jar bar.class ClassName\n"
					  + "options:\n"
					  + "  -r read units file (also: -Dunifi.read)\n"
					  + "  -w write units file (also: -Dunifi.write)\n"
					  + "  -gw write golden units file (also: -Dunifi.golden.write)\n"
					  + "  -gr read golden units file (also: -Dunifi.golden.read)\n"
					  + "  -dw write diff w.r.t. golden units (also: -Dunifi.diff.write)\n"
					  + "  -c <unifi control file>\n"
					  + "  -n <unit names file>\n"
					  + "  -v verbose output\n"
					  + "  -lp log properties (default: log.properties in current directory)\n");

	System.exit(1);
}

/**
 * parses the command line args and returns the remaining
 */
private static String[] parse_args (String args[])
{
	if (args.length == 0)
		print_usage_and_exit();

    int argno = 0;
    for (argno = 0; argno < args.length ; argno++)
    {
        if (args[argno].startsWith ("-"))
        {
            if (args[argno].equals ("-lp"))
                System.setProperty ("unifi.log.properties", args[++argno]);
            if (args[argno].equals ("-i"))
                System.setProperty ("unifi.instrument", "true");
            else if (args[argno].equals("-r"))
            	System.setProperty ("unifi.read", args[++argno]);
            else if (args[argno].equals("-gr"))
            	System.setProperty ("unifi.golden.read", args[++argno]);
            else if (args[argno].equals("-w"))
            	System.setProperty ("unifi.write", args[++argno]);
            else if (args[argno].equals("-c"))
            	System.setProperty ("unifi.control.file", args[++argno]);
            else if (args[argno].equals("-n"))
            	System.setProperty ("unifi.unit.names", args[++argno]);
            else if (args[argno].equals("-v"))
            	System.setProperty ("unifi.verbose", "true"); // actual value doesn't matter
            else if (args[argno].equals("-wl"))
            	System.setProperty ("unifi.watchlist", args[++argno]);
            else if (args[argno].equals("-gw"))
            	System.setProperty ("unifi.golden.write", args[++argno]);
            else if (args[argno].equals("-dw"))
            	System.setProperty ("unifi.diff.write", args[++argno]);
            else
            	System.err.println ("Unrecognized option: " + args[argno]);
        }
        else
            break;
    }

    // shift the remaining args and pass it back.
    String[] new_args = new String[args.length-argno];
    for (int i = argno ; i < args.length ; i++)
         new_args[i-argno] = args[i];
    return new_args;
}

/**
 * clear bcel repository cache - otherwise bcel retains
 * handles to all classes looked up and consumes memory.
 */
public static void clear_cache()
{
    // ideally, clear on some more intelligent policy.
    // e.g. only when package changes (clearing only
    // after a jar file is processed is too coarse)
    // but for now always clear
    Repository.clearCache();
}

/**
 * analyze the given jar file
 */
private static void analyze_jar_file (String name) throws IOException
{
    Tee ("Analyzing all classes in jar file \"" + name + '"');

    JarInputStream jin = new JarInputStream (new FileInputStream (name));

    // make sure, jar file exists, then incr jars processed filesize
    File f = new File(name);
    STATS.bump_jars_processed_filesize(f.length());

    JarFile jf = new JarFile (name);
    Enumeration e = jf.entries();

    while (e.hasMoreElements())
    {
        JarEntry je = (JarEntry) e.nextElement();
        if (je == null)
        {
            break;
        }

        // if it's a directory, we don't copy to the output jar file
        // because it causes problems if there are multiple jar files we
        // are trying to instrument - the entry for the directory could
        // already exist in the output jar file, and if so jout.putNextEntry
        // craps out
        if (je.isDirectory ())
        {
            continue;
        }

        Log.println ("Jar entry " + je.getName () +
                     ": " +
                     ( (je.getMethod () == java.util.zip.ZipEntry.DEFLATED) ?
                      "compressed, " : ", uncompressed, ") +
                     " (size = " + je.getSize () +
                     ", compressed size = " + je.getCompressedSize () + ")");

        if (je.getName ().endsWith (".class"))
        {
            analyze_class (get_clazz_from_istream (je.getName(), jf.getInputStream (je)));
        }
    }
    jin.close();

    // clear the cache to save memory
    clear_cache();
}

/**
 * analyze a class
 */
private static void analyze_class (JavaClass clazz)
{
    if (!nameFilter.select(clazz.getClassName()))
    {
        Tee ("Disabled: " + clazz.getClassName());
        if (clazz.isClass())
            STATS.incr_disabled_classes();
        else
            STATS.incr_disabled_interfaces();
        return;
    }
    Tee ("Analyzing class " + clazz.getClassName());
    if (clazz.isClass())
        STATS.incr_analyzed_classes();
    else
        STATS.incr_analyzed_interfaces();

    /*
        (clazz.getClassName().startsWith("org.omg") ||
        clazz.getClassName().startsWith("sun.rmi") ||
        clazz.getClassName().equals("org.omg.stub.javax.management.remote.rmi._RMIConnectionStub"))
        return;
        */

    Method[] methods = clazz.getMethods ();
    Log.println (methods.length + " methods");

    ConstantPoolGen cpgen = new ConstantPoolGen (clazz.getConstantPool ());

    Field[] fields = clazz.getFields ();
    for (Field f : fields)
    {
        // just make sure all fieldunits for this class's fields are created.
        // don't depend on the use of the
        // field to create the references. Sometimes it is useful to have a
        // unit for the field, even if there is no use of it anywhere (which
        // means it would otherwise never get created if we didn't do it here).
        // just recording that field's presence enables us to diff against other
        // versions of the program where that fields gets merged with something.

        // of course the field unit may already exist because there has been a
        // reference to it from elsewhere, in which case this does nothing.
        FieldUnit.get_field_unit (f.getType(), clazz.getClassName() + "." + f.getName(), f.isPublic(), f.isProtected(), f.isPrivate(), f.isStatic(), false);
        // discard the return value, because we want to just have the field unit
        // created and stored in the method directory.
    }

    for (int i = 0; i < methods.length; i++)
    {
        LineNumberTable line_num_table = null;

        if (methods[i].getCode () != null)
        {
            Attribute[] attribs = methods[i].getCode ().getAttributes ();
            if (attribs != null)
            {
                for (int j = 0; j < attribs.length; j++)
                {
                    if (attribs[j] instanceof LineNumberTable)
                    {
                        line_num_table = (LineNumberTable) attribs[j];
                    }
                }
            }
        }

        MethodGen mg = new MethodGen (methods[i], clazz.getClassName (),
                                      cpgen);

        // things break if we instrument system class constructors
        // at some point, ok to break the sun.*
        // not a big deal since it's constructors only
//        if (mg.getMethodName().equals ("<init>"))
//		    System.out.println ("Skipping because this is a constructor\n");
//        else
        if (!methods[i].isNative () && !methods[i].isAbstract ())
        {
            analyzeMethod (clazz, mg, cpgen, line_num_table);
        }
        else
        {
            Log.println ("(abstract or native)");
        }
    }
    clear_cache();
}

/**
 * analyze a single method
 */
private static void analyzeMethod (JavaClass clazz, MethodGen mg, ConstantPoolGen cpgen, LineNumberTable lnt)
{

    String full_sig = mg.getClassName () + "." + mg.getName () +
        mg.getSignature ();
    _logger.fine("-----------------------------------------------------------");
    Tee ("  Analyzing method " + full_sig + (mg.isStatic () ? "(static)" : "(non-static)"));

    if (!nameFilter.select(full_sig))
    {
        Tee ("Disabled: " + full_sig);
        STATS.incr_disabled_methods();
        return;
    }
    STATS.incr_analyzed_methods();

//    MethodUnits mue = MethodResolver.get_method_units (clazz, mg.getClassName(), mg.getName(), mg.getSignature(), mg.isPrivate(), mg.isStatic(), true);
    MethodUnits mue = MethodResolver.get_method_units (mg.getClassName(), mg.getName(), mg.getSignature(), mg.isPrivate(), mg.isStatic(), true);
    if (mue == null)
    {
        Tee ("Unable to find method: " + full_sig);
        return;
    }

    // the name selector filtering is done after the mapping
    String mapped_full_sig = mue.full_sig();
    if (!nameFilter.select(mapped_full_sig))
    {
        Tee ("Disabled: " + full_sig);
        return;
    }

    RD_DF_algorithm rd_alg = new RD_DF_algorithm ();

    if (lnt == null)
    {
        Log.println ("no line number information for method!");
    }
    InstructionList il = mg.getInstructionList ();
    il.setPositions ();

    _logger.fine ("started disambiguating local variables for " + full_sig);

    LogicalLVMap lv_map = (LogicalLVMap) rd_alg.analyze_method (mg, cpgen, lnt);
    lv_map.verify (mg, cpgen);

    _logger.finer ("Completing disambiguating local variables");
    _logger.finer ("lv_map's size = " + (lv_map.highest_lv () + 1));
    _logger.finer (lv_map.toString());

    mue.setupLocals (lv_map, mg, cpgen);
    unifi_DF_algorithm unifi_alg = new unifi_DF_algorithm ();
    unifi_alg.set_lv_map (lv_map);
    unifi_alg.setup_this_munits(mg);
    _logger.finer ("------------------- Started unifi analysis of method " + full_sig);
   unifi_alg.analyze_method (mg, cpgen, lnt);
    _logger.finer ("Done analyzing method " + full_sig);
}

// handle user friend names

private static void readDisplayNames(UnitCollection uc) throws IOException
{
	String filename = System.getProperty("unifi.unit.names");
	if (filename == null)
		return;
	File f = new File(filename);
	if (f.exists() && f.canRead())
		uc.readDisplayNames(filename);
}

private static boolean checkUnits() {
	String checkUnits = System.getProperty("unifi.golden.read");
	if (Util.nullOrEmpty(checkUnits))
		return false;

	return true;
}

/**
 * reads input file if any.
 * note: either unifi.read or unifi.golden.read should be defined, not both!
 * the only diff is that for golden.read, we do an extra pass over the units to mark the units golden
 */
public static void read_unit_collection_file() {
	String uc_filename = System.getProperty("unifi.read");
	boolean make_golden = false;

	if (Util.nullOrEmpty(uc_filename))
	{
		uc_filename = System.getProperty("unifi.golden.read");
		if (Util.nullOrEmpty(uc_filename))
		{
			_logger.fine ("No input units file");
			return;
		}
		make_golden = true;
	}

	_logger.info ("Reading input units from " + uc_filename + " make_golden = " + make_golden);

	ObjectInputStream ois;
	UnitCollection uc=null;
	try {
		ois = new ObjectInputStream(new FileInputStream(uc_filename));
	} catch (Exception e) {
		System.err.println("Warning: error opening file: " + uc_filename);
		throw new RuntimeException("Cannot continue without unit collection file.");
	}

	try {
		uc = (UnitCollection) ois.readObject();
		MethodResolver.globalMethodUnitsDir = (Map<String, MethodUnits>) ois.readObject();
		FieldUnit.globalFieldUnitDir = (Map<String, FieldUnit>) ois.readObject();
		ois.close();
		_logger.info ("Unit Collection: " + uc);
		_logger.info ("Method directory: " + MethodResolver.globalMethodUnitsDir.size() + " methods");
		_logger.info ("Field directory: " + FieldUnit.globalFieldUnitDir.size() + " fields");
	} catch (Exception e) {
		System.err.println("Warning: error reading data from file " + uc_filename);
		System.err.println(e);
		Util.ASSERT(false);
	}
	Unit._current_unit_collection = uc;

	uc.compute_reps();

	FieldUnit.verifyAllUnitsInUC(uc);

	uc.verify();
	System.out.println ("Unit collection read: " + uc);

	if (make_golden)
	{
		makeGolden(uc);
	}
}

private static void makeGolden(UnitCollection uc)
{
	uc.makeGolden();
	uc.assignUnitAndEventIds();
	// todo: clean up constraintset
}

// setup classpath to include all the .class and .jar files given in the args.
// this makes it easier to load classes.
// jars and dirs due to args are prepended to existing classpath.
// this will make sure that org.apache.bcel.util.Classpath.getClassPath()
// returns the right classpath when the BCEL repository is initialized.
// note: i am not sure whether setting the sys prop java.class.path will change the
// _actual_ java classpath. however, it does change the BCEL repository's classpath
// which is all we want since we will always ask the BCEL repo to load the classpath,
// and it reads the java.class.path directly (See BCEL source code for org.apache.bcel.util.ClassPath.getClassPath())
private static void setup_classpath(String jarsAndClasses[])
{
	String extra_cp = ""; // extra cp to be prepended to classpath due to args
	for (int argno = 0; argno < jarsAndClasses.length; argno++)
	{
		String arg = jarsAndClasses[argno];
		String extra_path_due_to_arg = ""; // extra path due to arg
	    if (arg.endsWith (".jar") || arg.endsWith(".zip"))
	    	extra_path_due_to_arg = arg;
	    else
	    {
	    	// if s is c.class, we want to add . to the cp. doesn't matter if '.' gets added multiple times
	    	// if s is a/b/c.class, we want to add a/b to the cp. i.e. strip everything after and including the last /
	    	// (I think it might be safe to leave a/b/ instead of a/b, but still...)
	    	// but tricky: if s is /c.class, we want just the / to remain
	        int idx = arg.lastIndexOf(File.separator);
	        if (idx == -1)
	        	extra_path_due_to_arg = ".";
	        else if (idx == 0)
	        	extra_path_due_to_arg = File.separator;
	        else
	        	extra_path_due_to_arg = arg.substring(0, idx);
	    }
	    extra_cp = extra_cp + extra_path_due_to_arg + File.pathSeparator;
	}

	// now set the system prop with the extra_cp prepended.
	String cp = System.getProperty("java.class.path");
	String final_cp = extra_cp + cp;
	System.setProperty ("java.class.path", final_cp);
	System.out.println ("Set java.class.path to " + final_cp);
}

private static void read_system_properties() {
	String constr = System.getProperty ("unifi.constraints", "true");
	if (constr.equalsIgnoreCase("false")) {
		System.out.println("setting doCompoundConstraints false.");
		doCompoundConstraints = false;
	}
}

public static void printConf(String[] jarsAndClasses)
{
	Tee ("UniFi " + unifi.Version.version);
	String s = System.getProperty("unifi.read");
	if (s != null)
		Tee ("Read units file: " + s);
	s = System.getProperty("unifi.write");
	if (s != null)
		Tee ("Write units file: " + s);
	s = System.getProperty ("unifi.control.file");
	if (s != null)
		Tee ("Control file: " + s);
	Tee ("Analyzing: ", false);
	for (String s1: jarsAndClasses)
		Tee (s1 + " ", false);
	Tee("\n", false);
}

/** sets up unifi parameters, prints out config and returns the list of classes and jars to analyze
 * @throws IOException */
public static String[] setup(String args[]) throws IOException
{
	System.out.print ("Running: java unifi.drivers.Analyze ");
	for (String arg : args) { System.out.print (arg + " "); }
	System.out.println ();
	System.out.println ("Current directory is: " + System.getProperty ("user.dir"));


	String[] jarsAndClasses = parse_args (args);

	read_system_properties();
	setup_logging();

	setup_classpath (jarsAndClasses);
	printConf (jarsAndClasses);
	WatchList.setup(System.getProperty("unifi.watchlist"));

	Tee ("Detailed log messages are being sent to file \"" + log_file_name +
	     "\"");

	try {
		nameFilter = new NameFilter();
		Tee ("Filter\n" + nameFilter);
	} catch (IOException ioe) {
	    Tee ("FATAL ERROR: Unable to open unifi.control.file");
	    System.exit (2);
	}
	return jarsAndClasses;
}

public static void main (String[] args) throws IOException
{
	String[] jarsAndClasses = setup(args);

	// read input units files if any
	read_unit_collection_file();

	try {
    for (int argno = 0; argno < jarsAndClasses.length; argno++)
    {
        if (jarsAndClasses[argno].endsWith (".jar"))
        {
            analyze_jar_file (jarsAndClasses[argno]);
        }
        else
        {
            analyze_class (get_clazz (jarsAndClasses[argno]));
        }
    }

    if (CONTEXT_SENSITIVE_ANALYSIS)
    {
    	UnitCollection uc = Unit._current_unit_collection;
    	ContextSensitiveAnalysis.doIt();

    	STATS.compute_munit_stats();
    	uc.set_stats(STATS);
    	System.out.println ("Unit collection before solving compound constraints: " + uc);
    	uc.compute_reps();
    	try { readDisplayNames(uc); } catch (Exception e) {
    		_logger.warning("Unable to read display names: " + e);
    	}
    	if (doCompoundConstraints)
    	{
//    		System.out.println ("***************** Compound constraints BEFORE prepare to solve\n" + uc.get_mult_constraints());
    		uc.prepare_to_solve(null);
    		System.out.println ("***************** Compound constraints AFTER prepare to solve\n" + uc.get_mult_constraints());
    		uc.solve_constraints();
    	}

    //	uc.print_units();
    	finish(Unit._current_unit_collection);
    //    unifi_state.getMethodDepTracker().printMethodDeps(System.out);
        ContextSensitiveAnalysis.printStats(System.out);
    }
    else
    {   // plain analysis
    	STATS.compute_munit_stats();
    	readDisplayNames(Unit._current_unit_collection);
    	finish(Unit._current_unit_collection);
    }

    System.out.println();
    SavedErrorMessages.print(System.err);
  //  unifi_state.getMethodDepTracker().printMethodDeps(System.out);

    // do gui stuff if enabled
    if (System.getProperty("unifi.gui") != null)
    {
        ThreadGroup gui_thread_group = new ThreadGroup ("GUI-threadgroup");
        _gui_thread = new Show(gui_thread_group);
        _gui_thread.start();
    }
	} finally {
		UnitCollection uc = Unit._current_unit_collection;
		_logger.info (uc + "\n" + Util.getMemoryStats());
		_logger.info ("after clearing unif events: ");
		uc.clearEvents();
		_logger.info (uc + "\n" + Util.getMemoryStats());
	}

    // wait for gui thread if running
    try {

    	if (_gui_thread != null)
    		_gui_thread.join();
    } catch (Exception e) { System.err.println ("GUI thread join failed!"); }
}

private static void finish(UnitCollection uc) throws IOException
{
	uc.verify();
    String filename = System.getProperty("unifi.write");
    String golden_filename = System.getProperty("unifi.golden.write");

    // remove unneeded units from a user p.o.v.
//    uc.remove_phi_units();
 //   uc.compute_reps();

    uc.compute_reps();
	uc.patch_types();
    uc.compute_reps();
	uc.verify();

	uc.assignUnitAndEventIds();
	uc.checkIds();

    // important: patch_types must have been called before removing any units
    // otherwise we'll wrongly remove units thinking they are not string or prim
	_logger.info ("Unit collection, all types: " + uc);
	if (REMOVE_NON_PRIMS)
	{
		uc.remove_non_prim_string_units();
		_logger.info ("Unit collection, only primitives and Strings: " + uc);
	}

    // compute reps again after removing units
//  uc.remove_clone_units();
//  uc.compute_reps();
	uc.verify();
	uc.assignUnitAndEventIds();
	uc.checkIds();

	/*
	String prefuseXMLFilename = System.getProperty("unifi.prefuse.filename");
	if (prefuseXMLFilename == null)
		prefuseXMLFilename = "PREFUSE.xml";
	uc.print_units_for_prefuse(prefuseXMLFilename);
	 */
	/*
    System.out.println("-------------------- Begin " + uc.get_units().size() + " final units");
    for (Unit u : uc.get_units())
        System.out.println ("U" + Integer.toString(++i) + ". " + u);
    System.out.println("-------------------- End final units ");

    i = 0;
    System.out.println("-------------------- Begin " + uc.get_events().size() + " final events");
    for (UnificationEvent e : uc.get_events())
        System.out.println ("E" + Integer.toString(++i) + ". " + e);
    System.out.println("-------------------- End final events");
    */

	uc.print_units();
	// uc.full_verify();
	_logger.info ("Final result: " + uc);

	// always check golden units
	UnitCollection diffUC = Unit._current_unit_collection.checkGoldenUnits();
	String diffFilename = System.getProperty("unifi.diff.write");
	if (diffUC.get_units().size() > 0 && !Util.nullOrEmpty(diffFilename))
	{
        try {
            ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream (diffFilename));
            oos.writeObject (diffUC);
            oos.writeObject(MethodResolver.globalMethodUnitsDir);
            oos.writeObject(FieldUnit.globalFieldUnitDir);
            oos.close();
        } catch (Exception e)
        {
                System.out.println ("Exception " + e);
                System.exit (2);
        }
	}

    if (filename != null)
    {
        try {
        ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream (filename));
        oos.writeObject (uc);
        oos.writeObject(MethodResolver.globalMethodUnitsDir);
        oos.writeObject(FieldUnit.globalFieldUnitDir);
        oos.close();
        } catch (Exception e)
        {
            System.out.println ("Exception " + e);
            System.exit (2);
        }
    }

	if (golden_filename != null)
	{
		makeGolden(uc);

		_logger.info ("Saving only golden units to " + golden_filename + ": " + uc);
		uc.print_units();
		ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream (golden_filename));
        oos.writeObject (uc);
        oos.writeObject(MethodResolver.globalMethodUnitsDir);
        oos.writeObject(FieldUnit.globalFieldUnitDir);
        oos.close();
	}
}

/* legacy code... not used any more */
@SuppressWarnings("unused")
private static void doFieldSeekingAnalysis() throws IOException
{
	System.out.println ("before eliminating polymorphism and considering all types");
	UnitCollection final_uc = FindPolymorphicMethods.eliminatePolymorphism(Unit._current_unit_collection);
	System.out.println ("after eliminating polymorphism and only considering primitive types and strings");
	final_uc.compute_reps();
	final_uc.verify();

	System.out.println ("***************** Compound constraints BEFORE prepare to solve\n" + final_uc.get_mult_constraints());
	final_uc.prepare_to_solve(null);
	System.out.println ("***************** Compound constraints AFTER prepare to solve\n" + final_uc.get_mult_constraints());

	if (doCompoundConstraints)
		final_uc.solve_constraints();

	STATS.compute_munit_stats();
	final_uc.set_stats (STATS);
	finish(final_uc);
}
}
