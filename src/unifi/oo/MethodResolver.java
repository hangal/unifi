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

/**
 * class to resolve method calls
 */

package unifi.oo;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.MethodGen;

import unifi.MethodUnits;
import unifi.UnitCollection;
import unifi.units.FieldUnit;
import unifi.units.MethodParamUnit;
import unifi.units.ReturnValueUnit;
import unifi.units.Unit;
import unifi.util.Pair;
import unifi.util.Util;

/** maps a method reference in the program code to its canonical method (based on OO rules etc).
 * Note: does NOT yet handle covariant return types! */

public class MethodResolver {

private static Logger logger = Logger.getLogger("unifi.methods");

/** maps a sig to a MethodUnits, used as a cache when looking up sigs */
public static Map<String,MethodUnits> globalMethodUnitsDir = new LinkedHashMap<String,MethodUnits>();

public static Collection<MethodUnits> get_all_method_units() {
    return globalMethodUnitsDir.values();
}

/**
 * returns Method object corresponding to target_methname and
 * target_param_sig in the given JavaClass (class or interface).
 * returns null if method is not found
 */
private static Method locate_method_in_class (JavaClass clazz, String target_methname, String target_param_sig, boolean must_be_static)
{
    int i = 0;
    Method[] methods = clazz.getMethods();
    if (logger.isLoggable(Level.FINEST)) {
        logger.finest("Looking up method name = " + target_methname + " sig = " + target_param_sig + " in class " + clazz.getClassName() + " must be static = " + must_be_static);
    }

    // check: does this contain only methods defined only in this class or inherited too ?
    for (; i < methods.length; i++)
    {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("Comparing with method name = " + methods[i].getName() + " sig = " + methods[i].getSignature() + " static = " + methods[i].isStatic());
        }

        if (methods[i].getName().equals (target_methname) &&
            methods[i].getSignature().equals (target_param_sig))
        {
            boolean is_static = methods[i].isStatic();
            if (must_be_static == is_static)
                return methods[i];
        }
    }
    logger.finest("Lookup failed");

    return null;
}

/** remove private methods from directory, e.g. when preparing to save public method's golden units */
public static void keepOnlyGolden()
{
	int count = 0;

	// remove private methods
	for (Iterator<Map.Entry<String, MethodUnits>> it = globalMethodUnitsDir.entrySet().iterator(); it.hasNext(); )
	{
		MethodUnits mu = it.next().getValue();
		if (mu != null && mu.isPrivate())
		{
			count++;
			it.remove();
		}
	}


	// now strip out the params and retvals that are not golden
	// also remove any methods that dont have any golden params or retvals
	int usefulUnitsLeft = 0;
	for (Iterator<Map.Entry<String, MethodUnits>> it = globalMethodUnitsDir.entrySet().iterator(); it.hasNext(); )
	{
		MethodUnits mu = it.next().getValue();
		if (mu != null)
		{
			mu.keepOnlyGolden();
			int nUsefulUnits = mu.nUsefulUnits();
			if (nUsefulUnits == 0)
				it.remove();
			usefulUnitsLeft += nUsefulUnits;
		}
	}

	logger.info (count + " private method units removed; Method directory now has " + globalMethodUnitsDir.size() + " entries with " + usefulUnitsLeft + " units");
}

/** returns method object for specified method definition
 * in any one of the superinterfaces of clazz (no guarantee about which superinterface)
 * clazz could be a class or interface.
 * (looked up method should not be private or static).
 * returns a pair <method=null, clazz=this_class> if method is not found.
 */
private static Pair<Method, JavaClass> locate_method_in_superinterfaces (JavaClass clazz, String method_name, String param_retval_sig)
{
    if (logger.isLoggable(Level.FINE)) {
        logger.fine("Trying to locate method in superinterfaces " + clazz.getClassName() + "." + method_name);
    }

    // lookup all superinterfaces of clazz
    try {
        JavaClass super_interfaces[] = clazz.getAllInterfaces();
        // the order of these superinterfaces from BCEL *seems* to be
        // from most generic to more specific, but like everything else in BCEL,
        // there are no guarantees
        // Note that getAllInterfaces returns ALL superinterfaces (not just immediately implemented
        // interfaces, but their superinterfaces too.)
        logger.finer ("superinterfaces length: " + super_interfaces.length);
        for (int i = 0; i < super_interfaces.length; i++)
        {
            Method m = locate_method_in_class (super_interfaces[i], method_name, param_retval_sig, false);
            if (m != null)
                return Pair.create (m, super_interfaces[i]);
        }
    } catch (ClassNotFoundException cnfe) { logger.severe ("Exception in looking up superinterfaces: " + cnfe); }

    return null;
}

/** returns a method and clazz pair for the given method definition
 * in clazz's most generic superclass, or itself
 * clazz must be a real class not an interface (and of course the method being
 * looked up should not be private but can be static).
 * returns null if method is not found.
 */
private static Pair<Method, JavaClass> locate_method_in_class_or_supers (JavaClass clazz, String method_name, String param_retval_sig, boolean is_method_static)
{
	if (clazz.isInterface())
	{
		logger.severe("not expecting an interface: " + clazz.getClassName() + " for method " + method_name + " " + param_retval_sig);
		return null;
	}

	if (logger.isLoggable(Level.FINE)) {
        logger.fine("Trying to locate method in class or supers " + clazz.getClassName() + "." + method_name);
    }

    // look first in all superclasses of clazz starting from most generic
    try {
        JavaClass supers[] = clazz.getSuperClasses();
        // supers is list of superclasses, from most specific to java.lang.Object
        // TODO: This might not be right - go the other way instead ?
        // worried about member hiding (superclass has member with the same name)
        for (int i = supers.length-1; i >= 0; --i)
        {
            Method m = locate_method_in_class (supers[i], method_name, param_retval_sig, is_method_static);
            if (m != null)
                return Pair.create(m, supers[i]);
        }
    } catch (ClassNotFoundException cnfe) { logger.severe ("Exception in looking up superclasses: " + cnfe); }

    // if not found in superclasses, look up in the current class
    Method return_method = locate_method_in_class(clazz, method_name, param_retval_sig, is_method_static);
    if (return_method == null)
    	return null;
    else
    	return Pair.create(return_method, clazz);
}

/**
 * for all interfaces (directly) implemented by clazz,
 * identifies the interfaces which have the specified method
 * and unifies mu_to_unify with the method units for those methods.
 * this uses lookup_or_allocate_munits on implemented interfaces.
 */
/*
private static void unify_interface_munits (JavaClass clazz, String method_name, String params_retval_sig, MethodUnits mu_to_merge)
{
    JavaClass all_interfaces[] = null;

    try {
        all_interfaces = clazz.getAllInterfaces();
        // this gets all interfaces implemented, not just the immediate superinterfaces
    } catch (ClassNotFoundException cnfe) {
        logger.fine ("WARNING: exception in looking up superclasses: " + cnfe);
        return;
    }

    for (int i = 0; i < all_interfaces.length; i++)
    {
        JavaClass intf = all_interfaces[i];
        // if this interface (or any of its superinterfaces) defines method m,
        // then unify mu_to_merge with each of them.
        // note: if clazz is an interface, then BCEL's impl of getAllInterfaces() is such
        // that all_interfaces will also contain itself. that's ok - we will harmlessly
        // unify the munits with itself.
        Method m = locate_method_in_class(intf, method_name, params_retval_sig, false);
        if (m != null)
        {
            // if (intf has method m, then we want to create a munits for it
            String intf_name = intf.getClassName();
            String intf_full_sig = intf_name + "." + method_name + params_retval_sig;

            if (logger.isLoggable(Level.FINE)) {
                logger.fine ("Resolver: Unifying " + clazz.getClassName() + " with interface method " + intf_full_sig);
            }

            ConstantPoolGen cpgen = new ConstantPoolGen (m.getConstantPool());
            MethodGen mgen = new MethodGen (m, intf_name, cpgen);
            MethodUnits mu = lookup_or_allocate_munits(intf, intf_full_sig, intf_name, mgen, params_retval_sig, false);
            mu_to_merge.unify (mu, null);
        }
    }
}
*/

private static void addUnitIfNeeded(Unit u)
{
	if (u != null)
	{
		if (u.toString().indexOf("Element of Param arg0 of method InputStream.read(byte[])") >= 0)
			Util.breakpoint();

		if (!Unit._current_unit_collection.get_units().contains(u))
			Unit.registerUnit(u);
		Util.ASSERT(Unit._current_unit_collection.get_units().contains(u));
	}
}

/** for units in golden method units that may not be saved in allUnits, we need to add them explicitly */
private static void addUnitAndRelatedUnits(Unit u)
{
	Unit orig_u = u;
	while (u != null)
	{
		addUnitIfNeeded(u);
		u = u.arrayOf;
	}

	u = orig_u;
	while (u != null)
	{
		addUnitIfNeeded(u);
		u = u.elementOf;
	}

	u = orig_u;
	if (u.lengthUnit != null)
		addUnitIfNeeded(u.lengthUnit);
	if (u.lengthOf != null)
		addUnitIfNeeded(u.lengthOf);
}

/**
 * returns munits for given call parameters.
 * typically a given sig could be "mapped" to another sig.
 * e.g. the method in its most generic superinterface or its most generic superclass.
 * the method directory is updated so that all future calls to this method sig will
 * return the mapped sig.
 * returns null if the method cannot be found on the classpath.
 */
public static MethodUnits get_method_units (String class_name,
        String method_name, String params_retval_sig,
        boolean is_method_private, boolean is_method_static, boolean is_in_analyzed_code)
{
    String full_sig = class_name + "." + method_name + params_retval_sig;
    if (logger.isLoggable(Level.FINE))
    	logger.fine("Method Resolver: looking up " + full_sig + (is_method_static ? " static" : " non-static") + (is_method_private ? " private" : " non-private"));

    // if we've seen this signature before, method units has already have been created
    // and all associated unification has already been done.
    MethodUnits m_units = globalMethodUnitsDir.get(full_sig);
    // if m_units not in analyzed code, but this invocation is in analyzed code,
    // then set is_in_analyzed_code.
    if ((m_units != null) && (!m_units.is_in_analyzed_code()) && is_in_analyzed_code)
    {
        m_units.set_is_in_analyzed_code (true);
        m_units.add_mapping_sig(full_sig);
    }

    if (logger.isLoggable(Level.FINE)) { logger.fine("Resolver lookup: first time we are seeing " + full_sig); }

    // get clazz object for the class. this could be a class or interface.
    JavaClass clazz = null; // givenClazz;
    if (clazz == null)
    	clazz = Util.get_JavaClass(class_name);
    if (clazz == null)
    {
        // we could probably create a MethodUnits anyway even if we don't find the class.
        // but it will be standalone and not unify with anything, so we can afford to ignore it
        String s = ("Unable to find class on classpath: " + class_name + " while looking for method: " + full_sig + " ("
                   + (is_method_private ? "private " : "non-private ")
                   + (is_method_static ? "static " : "non-static") + ")");
        logger.severe (s);
        globalMethodUnitsDir.put(full_sig, null);
        return null;
    }

    JavaClass mapped_clazz = null;
    Method mapped_method = null;
    boolean is_constructor = method_name.equals ("<init>");
    boolean is_static_initializer = method_name.equals ("<clinit>");

    // private and static methods: look up only in this class
    // otherwise look up in any of the implemented superinterfaces
    // otherwise look up in most specific superclasses
    // 26/11/07: WRONG! static methods also look up superclasses
    // used to be: if (is_method_private || is_method_static)
    // constructor and clinits should also not be looked up in superclasses.
    // NOTE: interfaces can have clinits!
    if (is_method_private || is_constructor || is_static_initializer)
    {
        mapped_method = locate_method_in_class(clazz, method_name, params_retval_sig, is_method_static);
        mapped_clazz = clazz;
    }
    else
    {
        logger.fine("method is not private, constructor or static initializer");
        Pair<Method, JavaClass> rv = locate_method_in_superinterfaces (clazz, method_name, params_retval_sig);

        // if method not found in clazz, mapped_method will be null.
        // so look up supers
        if (rv == null)
        {
            logger.fine("method " + method_name + params_retval_sig + " not found in superinterfaces of " + clazz.getClassName() + ", looking in supers or this class");
            rv = locate_method_in_class_or_supers(clazz, method_name, params_retval_sig, is_method_static);
        }

        if (rv != null)
        {
        	mapped_method = rv.getLeft();
        	mapped_clazz = rv.getRight();
        }
    }

    // mapped_method and mapped_clazz now represent the real method and clazz the original method map to.
    if (mapped_method == null)
    {
        // something is wrong. we know the clazz exists, but method does not ??
        // maybe a version problem.
        logger.severe ("Unable to locate method " + full_sig + ", including in superinterfaces or superclasses. Maybe a version mismatch");
        globalMethodUnitsDir.put(full_sig, null);
        return null;
    }

    String mapped_class_name = mapped_clazz.getClassName();
    String mapped_full_sig = mapped_class_name + "." + method_name + params_retval_sig;
    ConstantPoolGen mapped_cpgen = new ConstantPoolGen (mapped_method.getConstantPool());
    MethodGen mapped_mgen = new MethodGen (mapped_method, mapped_class_name, mapped_cpgen);
    if (logger.isLoggable(Level.FINE)) { logger.fine ("Resolver: Method " + full_sig + " is mapped to " + mapped_full_sig); }

    MethodUnits mapped_m_units = lookup_or_allocate_munits(mapped_clazz, mapped_full_sig, mapped_class_name, mapped_mgen, params_retval_sig, is_method_static, is_in_analyzed_code);
    mapped_m_units.add_mapping_sig(full_sig);

    // cache the mapping so we can use it if we see the sig again
    globalMethodUnitsDir.put(mapped_full_sig, mapped_m_units);

    if (!full_sig.equals(mapped_full_sig))
        globalMethodUnitsDir.put (full_sig, mapped_m_units);

    return mapped_m_units;
}

/** returns the munits for the specified method, creating it if it doesn't exist.
 *  clazz could be a class or interface.
 */
private static MethodUnits lookup_or_allocate_munits (JavaClass clazz, String full_sig,
              String class_name, MethodGen mgen, String params_retval_sig, boolean is_method_static, boolean is_in_analyzed_code)
{
    MethodUnits munits = globalMethodUnitsDir.get(full_sig);
    if (munits == null)
    {
        munits = new MethodUnits (full_sig, params_retval_sig, class_name, mgen, is_method_static, is_in_analyzed_code, clazz.isInterface());
        globalMethodUnitsDir.put (full_sig, munits);
    }

    return munits;
}

public static MethodUnits lookup (String full_sig)
{
	return globalMethodUnitsDir.get(full_sig);
}

/** ensures all units for all method units are in the uc's allUnits */
public static void verifyAllUnitsInUC(UnitCollection uc) {
	for (MethodUnits mu: globalMethodUnitsDir.values())
	{
                if (mu==null) continue;
		for (MethodParamUnit mpu: mu.get_param_units())
			if (mpu != null)
				if (!uc.contains(mpu))
					Util.die("Param unit:" + mpu + " not in UC");

		ReturnValueUnit rvu =	mu.get_return_value_unit();
		if (rvu != null)
			if (!uc.contains(rvu)) {
				Util.die("RV unit:" + rvu + " not in UC");
		}
	}
}
}
