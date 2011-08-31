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

import org.apache.bcel.generic.*;

import unifi.oo.MethodResolver;
import unifi.units.MethodParamUnit;
import unifi.units.ReturnValueUnit;

import java.util.*;
import java.io.*;

/** Stats reporting object for unifi */
public class Stats implements Serializable {

private long jars_processed_filesize; // note: tracks only jar file sizes, not class files.
private int n_classes, n_disabled_classes;
private int n_interfaces, n_disabled_interfaces;
private int n_method_bodies, n_disabled_method_bodies;
private int n_munits_in_analyzed_code;
private int n_total_params, n_prim_params, n_string_params;
private int n_total_retvals, n_prim_retvals, n_string_retvals;

public void incr_disabled_classes() { n_disabled_classes++; }
public void incr_disabled_interfaces() { n_disabled_interfaces++; }
public void incr_analyzed_classes() { n_classes++; }
public void incr_analyzed_interfaces() { n_interfaces++; }
public void incr_disabled_methods() { n_disabled_method_bodies++; }
public void incr_analyzed_methods() { n_method_bodies ++; }

/** need to report:
total # of classes
total # of interfaces
total # of method bodies analyzed in classes specified.
total # of method bodies not analyzed in classes specified (due to hashcode, compareTo, etc).
unique # of methods w.r.t munit mapping
total # of params/retvals
total # of primitive params/retval
total # of String params/retval
total # of non-polymorphic primitive params/retval
total # of non-polymorphic String params/retval

# of unification points per unit, excluding for 1-unit buckets.
*/

public void bump_jars_processed_filesize(long size)
{
    jars_processed_filesize += size;
}

public void compute_munit_stats()
{
    Type string_type = Type.getType("Ljava/lang/String;");

    Collection<MethodUnits> all_munits = MethodResolver.get_all_method_units();
    // all_munits will have dups, so remove them
    HashSet<MethodUnits> munits_set = new LinkedHashSet<MethodUnits>();
    munits_set.addAll(all_munits);

    // total_munits = all_munits.size();
    for (MethodUnits mu : munits_set)
    {
        if (mu == null)
            continue;
        if (mu.is_in_analyzed_code())
        {
            n_munits_in_analyzed_code++;

            MethodParamUnit params[] = mu.get_param_units();
            for (MethodParamUnit param : params)
            {
            	if (param == null)
            		continue;
                Type t = param.getType();
                if (t instanceof BasicType)
                    n_prim_params++;
                if (t.equals(string_type))
                    n_string_params++;
                n_total_params++;
            }
            ReturnValueUnit rvu = mu.get_return_value_unit();
            if (rvu != null)
            {
                Type t = rvu.getType();
                if (t instanceof BasicType)
                    n_prim_retvals++;
                if (t.equals(string_type))
                    n_string_retvals++;
                n_total_retvals++;
            }
        }
    }
}

public String toString()
{
    StringBuilder sb = new StringBuilder();
    sb.append ("total jar file size: " + jars_processed_filesize + " bytes (note: includes only .jar's analyzed, not .class)\n");
    // note: tracks only jar file sizes, not class files.
    sb.append ("Number of classes = " + n_classes + " (" + n_disabled_classes + " disabled)\n");
    sb.append ("Number of interfaces = " + n_interfaces + " (" + n_disabled_interfaces + " disabled)\n");
    sb.append ("Number of method bodies analyzed = " + n_method_bodies + " (" + n_disabled_method_bodies + " disabled)\n");

    sb.append ("Number of distinct method interfaces: " + n_munits_in_analyzed_code +"\n");
    sb.append ("Params w.r.t munit bodies: " + n_total_params + " (" + n_prim_params + " primitive, " + n_string_params + " String, " + (n_prim_params + n_string_params) + " either)\n");
    sb.append ("Return values w.r.t munit bodies: " + n_total_retvals + " (" + n_prim_retvals + " primitive, " + n_string_retvals + " String, " + (n_prim_retvals + n_string_retvals) + " either)\n");
    return sb.toString();
}

}
