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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.bcel.generic.MethodGen;

import unifi.df.*;
import unifi.oo.MethodResolver;
import unifi.rd.LogicalLVMap;
import unifi.units.MethodParamUnit;
import unifi.units.ReturnValueUnit;
import unifi.units.Unit;
import unifi.util.Util;

public class unifi_DF_algorithm extends DF_algorithm
{
	private static Logger bb_logger = Logger.getLogger("unifi.basicblocks");

	/* this is a hack... we are using the algorithm object to store state common to a method */
    public static LogicalLVMap _current_lv_map;
    public static MethodUnits _current_munits;

    public unifi_DF_algorithm ()
    {
        try
        {
            _bb_type = Class.forName ("unifi.df.BasicBlock"); // vanilla BB will do for us, since we are not computing any state inside the BB
            _state_type = Class.forName ("unifi.unifi_state");
        }
        catch (Exception e)
        {
            System.out.println ("FATAL EXCEPTION: " + e);
            Util.ASSERT (false);
        }
    }

    public void set_lv_map (LogicalLVMap lvm)
    {
        _current_lv_map = lvm;
    }

    /** sets up this_munits for all the bb's in this method */
    public void setup_this_munits(MethodGen mg)
    {
	    String params_return_sig = mg.getSignature();
	    String full_sig = mg.getClassName () + "." + mg.getName () + mg.getSignature ();

	    if (bb_logger.isLoggable(Level.FINER)) { bb_logger.finest ("Analyzing method body with full_sig = " + full_sig); }
	    // Note: should we apply name filter here too ?
	    _current_munits = MethodResolver.get_method_units (mg.getClassName(), mg.getName(), params_return_sig, mg.isPrivate(), mg.isStatic(), true);
	    if (bb_logger.isLoggable(Level.FINER)) { bb_logger.finest ("Resolved to this_munits"); }

	    // verify that method's params and ret vals are properly registered... had trouble with this before
		for (MethodParamUnit mpu: _current_munits.get_param_units())
			if (mpu != null)
				Util.ASSERT(Unit._current_unit_collection.get_units().contains(mpu));
		ReturnValueUnit rvu = _current_munits.get_return_value_unit();
		if (rvu != null)
			Util.ASSERT(Unit._current_unit_collection.get_units().contains(rvu));
    }

    protected void init_for_method ()
    { /* */ }

}
