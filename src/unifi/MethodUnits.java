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


import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.logging.*;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

import unifi.contextsensitive.MethodInvoke;
import unifi.contextsensitive.MethodSummary;
import unifi.oo.MethodResolver;
import unifi.rd.LogicalLVMap;
import unifi.units.FieldUnit;
import unifi.units.LocalVarUnit;
import unifi.units.MethodParamUnit;
import unifi.units.ReturnValueUnit;
import unifi.units.Unit;
import unifi.util.BCELUtil;
import unifi.util.Util;

/** a data structure for a method interface.
 * maintains params and return values.
 * the effect of the method is in the method summary
 * contained inside this object.
 */
public class MethodUnits implements Serializable
{
	public static final long serialVersionUID = 1L;
	private static Logger logger = Logger.getLogger("unifi.MethodUnits");
	private static Set<String> ignoreList; // these method params/retvals will be ignored, e.g. cos they are polymorphic
	// format full_sig--n
	// where n = position of parameter (including 0 for this in instance methods) and -1 for retvals
	static {
		readIgnoreList();
	}
	/** map of <BCP at call site to this method> -> method summary for that call site */
	private static Map<BCP,MethodSummary>bcpToCloneMap = new LinkedHashMap<BCP, MethodSummary>();

	// mappings_sigs are sigs of method **implementations (or overrides)** that map to this method
	private final Set<String> _mapping_sigs = new LinkedHashSet<String>(); /** sigs of all methods that map to this MethodUnit */

	boolean isPublicOrProtected = true;
	LocalVarUnit[] _local_vars;
	MethodParamUnit[] _params; // array of n_param_words (not n_params!) i.e. 2 entries for longs/doubles.
	ReturnValueUnit _rv; // == null iff return type void
	private final String _full_sig;
	private boolean is_interface_method; // specifies if this method belongs to an interface

	private MethodSummary summary; // method summary unit collection for this method's body

	/** is_in_analyzed_code: if false: this munits was created due to an invoke to an external (library) method
 	if true: this munits is connected to a method M which is declared in code to be analyzed.
 	(this munits could be for just M or for a superclass or superinterface method of M) */
	private boolean _is_in_analyzed_code;

	// is read from a golden units file ?
	private boolean isGolden;

	private int _n_param_words, _n_params;

	public boolean isPrivate() { return !isPublicOrProtected; }

	public MethodSummary getMethodSummary() { return summary; }
	public MethodSummary getMethodSummaryClone(MethodInvoke mi) { return summary.cloneSummary(mi); }

	public String full_sig() { return _full_sig; }
	public boolean is_in_analyzed_code() { return _is_in_analyzed_code; }
	public void set_is_in_analyzed_code(boolean b)
	{
		Util.ASSERT (b); // we can only set this to true
		// set this state on this method's params and retval also
		for (MethodParamUnit param : _params)
			param.set_is_in_analyzed_code(b);
		if (_rv != null)
			_rv.set_is_in_analyzed_code(b);
		_is_in_analyzed_code = b;
	}

	public boolean isGolden() { return isGolden; }

	private static void readIgnoreList()
	{
		String filename = System.getProperty("ignore.methods");
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
	/** reduce size of this object in preparation for being stored as golden units */
	public void keepOnlyGolden()
	{
		isGolden = true;
		// remove pointers to all clones
		bcpToCloneMap.clear();

		// null out params and retval if they are not golden, we won't be needing them
		for (int i = 0; i < _params.length; i++)
			if (_params[i] != null && !_params[i].isGolden())
				_params[i] = null;

		if (_rv != null && !_rv.isGolden())
			_rv = null;

		// just reset the summary
		summary = new MethodSummary(this, _params, _rv);
		_local_vars = null;
	}

	/** how many of these method units's params + retval are non-null ? */
	public int nUsefulUnits()
	{
		int count = 0;
		for (int i = 0; i < _params.length; i++)
			if (_params[i] != null)
				count++;
		if (_rv != null)
			count++;

		return count;
	}

	public void add_mapping_sig (String full_sig) { _mapping_sigs.add (full_sig); }

	/** returns # of declared params */
	public int get_n_params() { return _n_params; }
	/** return whether this munits has a return value */
	public boolean has_retval() { return (_rv != null); }
	public boolean is_interface_method() { return this.is_interface_method; }

	// mg can be null - only impact is that the local var table is not available
	// new should be called ONLY by MethodResolver
	public MethodUnits (String full_sig, String params_return_sig, String class_name, MethodGen mg, boolean is_method_static, boolean is_in_analyzed_code, boolean is_interface)
	{
		_full_sig = full_sig;
		this.is_interface_method = is_interface;

		// check consistency of input params if we're given an mg
		if (mg != null)
		{
			if (logger.isLoggable(Level.FINE)) { logger.fine ("Creating method units for: " + full_sig); }
			String sig = mg.getClassName () + "." + mg.getName () + mg.getSignature ();
			Util.ASSERT (full_sig.equals(sig));

			// remember to check static v/s instance even if the name matches
			is_method_static = mg.isStatic ();
			// check perms on method
			isPublicOrProtected = (mg.isPublic() || mg.isProtected());
		}

		Type argTypes[] = Type.getArgumentTypes (params_return_sig);
		Type returnType = Type.getReturnType (params_return_sig);

		if (returnType != Type.VOID)
		{
			String rv_desc = full_sig + "--" + "-1";
			if (ignoreList != null && ignoreList.contains(rv_desc))
				_rv = null;
			else
			{
				_rv = new ReturnValueUnit (full_sig, returnType, isPublicOrProtected, is_in_analyzed_code);
				Unit.registerUnit (_rv);
			}

			// get arg types of _params[] excluding the 'this' reference
		}

		_n_params = argTypes.length;

		// find total no. of param words
		int total_no_of_words = 0;
		for (int ix = 0; ix < argTypes.length; ix++)
		{
			total_no_of_words += argTypes[ix].getSize ();
		}

		if (!is_method_static)
		{
			total_no_of_words++;
		}
		_n_param_words = total_no_of_words;

		// create one methodParamUnit per arg, including one for 'this' if needed
		// for args of size 2 words, initialize to the same MethodParamUnit

		_params = new MethodParamUnit[_n_param_words];
		int word_ptr = 0;
		int arg_ptr = 0;

		if (!is_method_static)
		{
			// type of params[0] (this) is mg's class
			// Type.getType() needs it in the sig format, i.e. "L<class>;"
			String object_sig = "L" + class_name + ";";
			object_sig = object_sig.replace ('.', '/');
			// _logger.fine ("The object sig : " + object_sig);
			String param_desc = full_sig + "--" + arg_ptr;
			if (ignoreList != null && ignoreList.contains(param_desc))
				_params[word_ptr] = null;
			else
			{
				_params[word_ptr] = new MethodParamUnit (Type.getType (object_sig), full_sig, mg, arg_ptr, isPublicOrProtected, is_in_analyzed_code);
				Unit.registerUnit (_params[word_ptr]);
			}
			word_ptr++;
			arg_ptr++;
		}

		for (int i = 0; i < argTypes.length; i++)
		{
			MethodParamUnit mpue;
			String param_desc = full_sig + "--" + arg_ptr;

			// note: word_ptr is used below because we need
			// the word index to look up the name of the variable
			if (ignoreList != null && ignoreList.contains(param_desc))
				mpue = null;
			else
			{
				mpue = new MethodParamUnit (argTypes[i], full_sig, mg, word_ptr, isPublicOrProtected, is_in_analyzed_code);
				Unit.registerUnit (mpue);
			}
			arg_ptr++;

			for (int j = 0; j < argTypes[i].getSize (); j++)
			{
				_params[word_ptr] = mpue;
				word_ptr++;
			}
		}

		// TODO: alternatively: read summary from a file e.g. for a library method if its been precomputed and stored
		summary = new MethodSummary(this, _params, _rv);

		Util.ASSERT (word_ptr == total_no_of_words);
	}

	/** merge all params and return values of a munit with another */
	/*
public void unify(MethodUnits other, BCP bcp)
{
    if (_logger.isLoggable(Level.FINE)) { _logger.fine ("Merging method units " + this + " WITH " + other); }
    util.ASSERT (_params.length == other._params.length);
    // if this guy has a return value, so should the other
    util.ASSERT ((_rv != null) == (other._rv != null));

    for (int i = 0; i < _params.length; i++)
        _params[i].unify (other._params[i], bcp);
    if (_rv != null)
        _rv.unify (other._rv, bcp);
}
	 */

	/** setLocals: sets the total # of local words (including param words)
 	for this method and allocates local var units.
 	Note: the local var count and localvar units are different for different method bodies
 	which map to the same MethodUnit. */
	public void setupLocals (LogicalLVMap lv_map, MethodGen mg, ConstantPoolGen cpgen)
	{
		// TOFIX: why do we add +2 here ?
		int max_local_words = lv_map.highest_lv () + 2;
		int real_locals = max_local_words - _n_param_words;
		_local_vars = new LocalVarUnit[real_locals];
		int local_index = _n_param_words; // local var index starts after params

		LocalVariableTable lvt = mg.getLocalVariableTable (cpgen);
		LocalVariable[] locvars = lvt.getLocalVariableTable ();

		if (logger.isLoggable(Level.FINE))
		{
			logger.fine ("max local vars = " + max_local_words + " params = " + _n_param_words);
			for (int m = 0; m < locvars.length; m++)
			{
				logger.finest ("LocalVariable " + m + " is : " +
						locvars[m].getName() + ", range is ("
						+ locvars[m].getStartPC() + "-"
						+ (locvars[m].getStartPC()+locvars[m].getLength())
						+ "), index " + locvars[m].getIndex());
			}
		}

		for (int i = 0; i < real_locals; i++)
		{
			int pos = lv_map.logical_LV_num_to_pos (i + _n_param_words);
			int physical_index = -1;
			String varname = null;
			String var_typesig = null;

			if (pos != -1)
			{
				physical_index = lv_map.pos_to_phys_LV_num (pos);

				// sometimes we don't get a proper match on the getStart() and getEnd()
				// positions of a LocalVariableGen
				// so we approximate and use the closest local var available to the
				// given pos, with the same index.
				int closest_distance = Integer.MAX_VALUE;
				int closest_distance_idx = -1;

				boolean certain = false; // tracks if we are certain about the name and type of this var access ?

				for (int tmp = 0; tmp < locvars.length; tmp++)
				{
					int idx = locvars[tmp].getIndex ();
					if (idx != physical_index)
						continue;
					int start_pc = locvars[tmp].getStartPC();
					int end_pc = locvars[tmp].getStartPC() + locvars[tmp].getLength();
					if ((pos >= start_pc) && (pos <= end_pc))
					{
						varname = locvars[tmp].getName ();
						var_typesig = locvars[tmp].getSignature();
						certain = true;
						break;
					}
					else
					{
						// take the mod of the distances from the 2 ends of the interval
						int distance1 = start_pc - pos;
						if (distance1 < 0)
							distance1 = -distance1;
						int distance2 = end_pc - pos;
						if (distance2 < 0)
							distance2 = -distance2;
						int min_distance = (distance1 < distance2) ? distance1 : distance2;
						if (min_distance < closest_distance)
						{
							closest_distance_idx = tmp;
							closest_distance = min_distance;
						}
					}
				}

				if (!certain)
				{
					if (logger.isLoggable(Level.FINE)) {
						logger.fine ("Warning: local variable position information may not be correct"
								+ ", distance " + closest_distance);
					}
					// may need to relax this - ok even if closest_distance_idx < 0 ?
					if (closest_distance_idx >= 0)
					{
						varname = locvars[closest_distance_idx].getName() + "?";
						var_typesig = locvars[closest_distance_idx].getSignature();
					}
					else
					{
						if (logger.isLoggable(Level.FINE)) {
							logger.fine ("Warning: No variable names mapped to index " + physical_index
									+ " in this method");
						}
					}
				}

				Type t = null;
				if (var_typesig != null)
				{
					t = Type.getType(var_typesig);
					if (t == null)
						logger.severe("local var: typesig = " + var_typesig + " but type = null!!");
				}

				// t may still be null. shrug
				_local_vars[i] = new LocalVarUnit (mg, cpgen, local_index, physical_index,
						pos,
						varname, t, certain);

				if (logger.isLoggable(Level.FINEST))
				{
					logger.finest ("local var: local = " + local_index + " index = " +
							physical_index + " pos = " + pos +
							" varname = " + varname);
				}

				Unit.registerUnit (_local_vars[i]);
			}
			local_index++;
		}

		// some var types may be wrong based on bad local var info (they are guessed, so marked !certain())
		// so check if the local var access instruction is compatible (aload, fload, iload etc)
		// is compatible with the type we've assigned to the var.
		// if we're not certain, then assign the type based on the guessed type from the instruction
		patch_local_var_types_based_on_insn_type(lv_map, mg, cpgen);
	}

    /** fix local var unit units based on the type of instruction */
    public void patch_local_var_types_based_on_insn_type(LogicalLVMap lv_map, MethodGen mg, ConstantPoolGen cpgen)
    {
      	Instruction[] insns = mg.getInstructionList().getInstructions();
    	int pos = 0;

		// now walk through all local var insns, tracking the state of the logical_lv
    	for (Instruction insn: insns)
    	{
    		if (insn instanceof LocalVariableInstruction)
    		{
    			// compute logical indx
    			LocalVariableInstruction lv_insn = (LocalVariableInstruction) insn;
    			int idx = lv_insn.getIndex();
    			int logical_idx = lv_map.pos_to_logical_LV_num(pos);
    			logical_idx -= _n_param_words; // adjust for the fact that _local_vars[] does not include method params but the logical_lv_num does

    			if (logical_idx >= 0 && _local_vars[logical_idx] != null)
    			{
        			Type insnType = lv_insn.getType(cpgen);
        			Type simplifiedInsnType = BCELUtil.simplifiedType(insnType);
	    			Type varType = _local_vars[logical_idx].getType();
	    			Type simplifiedVarType = BCELUtil.simplifiedType(varType);

	    			if (!_local_vars[logical_idx].isCertain() &&
	    				(simplifiedVarType != simplifiedInsnType))
	    			{
	    				logger.finer ("overriding type for unit: " + _local_vars[logical_idx] + " from type " + varType + " to " + simplifiedInsnType);
	    				_local_vars[logical_idx].forceType(simplifiedInsnType);
	    			}
    			}
    		}
    		pos += insn.getLength();
    	}
    }

	/** given a LV index, returns the unit element associated with that index
	 // this should already be a "clean" idx. */
	public Unit get_local_var_unit (int idx)
	{
	    System.out.println("???????????????? idx = " + idx + " no.of words : " + _n_param_words+" local vars length: "+_local_vars.length);
		
	    //TODO:Fix the outOfBoundException occuring here
	    //Temp Fix
	    //if((idx<0)||(idx>=(_local_vars.length+_n_param_words)))
	    //return _local_vars[0];
	    
	    if (idx >= _n_param_words) //returning local var's unit element
		{
			return _local_vars[idx - _n_param_words];
		}
		else
		{
			return _params[idx]; // returning param's unit element
		}
	}

	public MethodParamUnit[] get_param_units() { return _params; }
	public void nullify_method_param_unit(int idx) { _params[idx] = null; }

	public ReturnValueUnit get_return_value_unit() { return _rv; }
	public void nullify_return_value_unit() { _rv = null; }

	/** returns rv unit at call site */
	public ReturnValueUnit get_return_value_unit_at (BCP bcp, MethodUnits callerMethodUnits, MethodUnits calleeMethodUnits)
	{
		MethodSummary ms = bcpToCloneMap.get(bcp);
		if (ms == null)
		{
			MethodInvoke mi = new MethodInvoke(bcp, callerMethodUnits, calleeMethodUnits);
			ms = summary.cloneSummary(mi);
			bcpToCloneMap.put(bcp, ms);
		}
		return ms.getReturnValueUnit();
	}

	// returns array of param_units for this method at call site (i.e. those of a clone).
	// NOTE: # of elements in this array = # of words, not # of
	// parameters. for longs and doubles, the same MethodParamUnit
	// is present twice onto the stack.
	public MethodParamUnit[] get_param_units_at (BCP bcp, MethodUnits callerMethodUnits, MethodUnits calleeMethodUnits)
	{
		MethodSummary ms = bcpToCloneMap.get(bcp);
		if (ms == null)
		{
			MethodInvoke mi = new MethodInvoke(bcp, callerMethodUnits, calleeMethodUnits);
			ms = summary.cloneSummary(mi);
			bcpToCloneMap.put(bcp, ms);
		}
		return ms.getParamUnits();
	}

	public String toString()
	{
		return "Method units for " + _full_sig;
	}

	public String full_toString()
	{
		StringBuilder sb = new StringBuilder(toString());
		sb.append ("Signatures which map to this method: " + "\n");
		for (String s : _mapping_sigs)
			sb.append (s);
		return sb.toString();
	}

	public void verify()
	{
		summary.verify();
	}
}
