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

package unifi.rd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.Type;

import unifi.util.BCELUtil;
import unifi.util.Util;

/** class to map a bcp to logical and physical local var slot. holds the result of
 * disambiguating local var slots after doing reaching defs. */

public class LogicalLVMap
{
    Map<Integer, Integer> _pos2_logical_lv_num;
    Map<Integer, Integer> _pos2_phys_lv_num;

// _pos2_new_lv_num is a hashmap from instruction position -> new lv num
    public LogicalLVMap ()
    {
        _pos2_logical_lv_num = new LinkedHashMap<Integer, Integer> ();
        _pos2_phys_lv_num = new LinkedHashMap<Integer, Integer> ();
    }

// at position pos, set the original and new lv numbers
    public void add (int pos, int phys_lv_num, int logical_lv_num)
    {
        Integer p = new Integer (pos);
        Integer new_lv = new Integer (logical_lv_num);
        Integer orig_lv = new Integer (phys_lv_num);

        Integer existing_new_lv = _pos2_logical_lv_num.get (p);
        if (existing_new_lv != null)
        {
            if (existing_new_lv.intValue () != logical_lv_num)
            {
                Util.ASSERT (false,
                             "Error, new lv num = " + logical_lv_num + ", existing " +
                             existing_new_lv.intValue () + " for pos " + pos);

            }
        }
        _pos2_logical_lv_num.put (p, new_lv);
        _pos2_phys_lv_num.put (p, orig_lv);
    }

    public int pos_to_phys_LV_num (int pos)
    {
        Integer I = _pos2_phys_lv_num.get (new Integer (pos));
        Util.ASSERT (I != null);
        return I.intValue ();
    }

// maps a given logical LV num back to it's pos (one of it's pos's)
    public int logical_LV_num_to_pos (int logical_lv_num)
    {
        for (Iterator it = _pos2_logical_lv_num.keySet ().iterator ();
             it.hasNext (); )
        {
            Integer P = (Integer) it.next ();
            Integer LV = _pos2_logical_lv_num.get (P);
            Util.ASSERT (LV != null);
            if (LV.intValue () == logical_lv_num)
            {
                return P.intValue ();
            }
        }

        return -1;
    }

    public int pos_to_logical_LV_num (int pos)
    {
        Integer I = _pos2_logical_lv_num.get (new Integer (pos));
        Util.ASSERT (I != null);
        return I.intValue ();
    }

// returns the highest numbered LV (in the new numbering) in this lv_map
    public int highest_lv ()
    {
        int max = -1;
        Util.ASSERT (_pos2_logical_lv_num.size () == _pos2_phys_lv_num.size ());

        // TODO: does this work for doubles ?
        for (Iterator it = _pos2_logical_lv_num.values ().iterator ();
             it.hasNext (); )
        {
            Integer I = (Integer) it.next ();
            int i = I.intValue ();
            if (i > max)
            {
                max = i;
            }
        }

        return max;
    }

    public String toString ()
    {
    	// sort keys so output is a bit more readable
    	List<Integer> keys = new ArrayList<Integer>();
    	keys.addAll(_pos2_logical_lv_num.keySet());
    	Collections.sort(keys);
    	
        StringBuffer sb = new StringBuffer ("Logical LV Map\n");
        for (Integer pos: keys)
        {
            Integer new_lv = _pos2_logical_lv_num.get (pos);
            Integer orig_lv = _pos2_phys_lv_num.get (pos);
            sb.append ("pos " + pos + ":" + new_lv + " (physical " + orig_lv + ")\n");
        }

        return sb.toString ();
    }

    public void verify ()
    {
    }

    /** verifies that the map is consistent with respect to types and sizes of local var operations.
     * i.e. each logical var must have the same type and size wherever it is referenced */
    public void verify(MethodGen mg, ConstantPoolGen cp)
    {
    	Instruction[] insns = mg.getInstructionList().getInstructions();
    	int pos = 0;

    	// we'll track state of all logical lv's.
    	// find the highest lv index and allocate the state array accordingly
		Type[] state = new Type[highest_lv()+1];

		// now walk through all local var insns, tracking the state of the logical_lv
    	for (Instruction insn: insns)
    	{
    		if (insn instanceof LocalVariableInstruction)
    		{
    			LocalVariableInstruction lv_insn = (LocalVariableInstruction) insn;
    			int idx = lv_insn.getIndex();
    			Util.ASSERT(_pos2_phys_lv_num.get(pos) == idx);

    			int logical_lv = _pos2_logical_lv_num.get(pos);
    			Type t = BCELUtil.simplifiedType(lv_insn.getType(cp));

    			if (state[logical_lv] != null)
    				Util.ASSERT(state[logical_lv] == t);
    			else
    				state[logical_lv] = t;
    		}
    		pos += insn.getLength();
    	}
    }

}
