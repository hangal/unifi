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


package unifi.oo;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import unifi.units.FieldUnit;
import unifi.util.Util;

/** this class has methods to resolve fields names according to OO rules.
 * non-static field names are resolved to the field with the same name in the same or the most specific superclass.
 */
public class FieldResolver {

public static Logger parentLogger = Logger.getLogger("unifi");
private static Logger logger = Logger.getLogger("unifi.fields");
static { logger.setParent(parentLogger); }

/**
 * returns non-static Field object corresponding to field_name and target type in the given JavaClass.
 * returns null if class doesn't have that field.
 */
private static Field getFieldInClass(JavaClass clazz, String target_field_name, String target_field_sig, boolean is_static)
{
    int i = 0;
    Field[] fields = clazz.getFields(); // this gets only the fields declared directly in clazz, not inherited fields
    for (; i < fields.length; i++)
    {
        if (fields[i].getName().equals (target_field_name) &&
            fields[i].getSignature().equals (target_field_sig))
        {
            if (is_static == fields[i].isStatic())
                return fields[i];
        }
    }

    return null;
}

/** returns a unit for the field with the given name and type.
 * maps to the defined field in the most specific superclass for a non-static field,
 * so that we have a canonical name and unit for the field storage.
 * for a static field, maps directly only className.fieldName. (XXX: this may not be right)
 * Note: a non-static field with name F is distinct from and will hide a non-static field F in its superclass.
 * (JLS 8.3) */
public static FieldUnit getFieldUnit (Type t, String className, String fieldName, boolean isStatic, boolean real_use)
{
	// mapped field name is the one this field name maps to
	// initially maps to the fieldname in className
	String orig_field_name = className + "." + fieldName;
	String mapped_field_name = orig_field_name;
    JavaClass clazz = Util.get_JavaClass(className);
    boolean isPrivate=false, isProtected = false, isPublic = false;

    if (clazz == null && logger.isLoggable(Level.INFO))
    	logger.warning ("Class not found on class path while looking up: " + mapped_field_name);

    Field f = null;
    if (clazz != null)
        f = getFieldInClass (clazz, fieldName, t.getSignature(), isStatic);
    // if field is non-static, look up supers.
    // if clazz is not available or field is static, just use the given classname
    // TODO: probably need to get red of the isStatic here, I suspect static fields
    // need to be looked up in superclasses too.
    if (clazz != null && !isStatic)
    {
    	// first lookup fieldname in this class
		if (f == null)
		{
			// not found in this class, look up supers
			try {
				JavaClass supers[] = clazz.getSuperClasses();
				// supers is list of superclasses, from most specific to java.lang.Object
				// note this is opposite to MethodResolver where we look for most generic
				// class with a method of the same sig
				for (int i = 0; i < supers.length-1; i++)
				{
					f = getFieldInClass (supers[i], fieldName, t.getSignature(), isStatic);
					if (f != null)
					{
						mapped_field_name = supers[i].getClassName() + "." + fieldName;
						break;
					}
				}
			} catch (ClassNotFoundException cnfe) {
				logger.warning ("Class not found on class path when looking up superclasses: " + cnfe);
			}
		}
    }
    if (f != null)
    {
		isPrivate = f.isPrivate();
		isPublic = f.isPublic();
		isProtected = f.isProtected();
    }

    FieldUnit fu = FieldUnit.get_field_unit(t, mapped_field_name, isPublic, isProtected, isPrivate, isStatic, real_use);
    if (logger.isLoggable(Level.FINE))
    	logger.fine ("Look up for field: " + orig_field_name + ", returns " + fu);

    return fu;
}

}
