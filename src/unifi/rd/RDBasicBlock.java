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

import java.util.*;
import java.util.logging.*;

import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

import unifi.df.*;
import unifi.util.Util;

public class RDBasicBlock extends BasicBlock
{

private static Logger logger = Logger.getLogger("unifi.RDBasicBlock");

    private Set _gen, _kill;

    public Set get_gen ()
    {
        return _gen;
    }

    public Set get_kill ()
    {
        return _kill;
    }

    public void init ()
    {
      /* */
    }

    /* def_list is the set of ALL def's in this method */
    public void compute_gen_kill_sets (Collection all_defs)
    {
        InstructionHandle begin = _begin_ih;
        InstructionHandle end = _end_ih;
        InstructionHandle prev_begin = null;

        while (prev_begin != end)
        {
            Instruction insn = begin.getInstruction ();

            int index = 0;
            int position = 0;

            if (insn instanceof StoreInstruction || insn instanceof IINC)
            {
                index = ( (IndexedInstruction) insn).getIndex ();
                position = begin.getPosition ();

                // first compute kill set = all defs to same index (including current insn)
                for (Iterator itr = all_defs.iterator (); itr.hasNext (); )
                {
                    Def d = (Def) itr.next ();
                    if (d.get_index () == index)
                    {
                        _kill.add (d);
                    }
                }

                /* next compute gen set :
                   find this def, add it to gen set
                     remove any other defs which may already be present in gen set
                   note subtlety here in case of a bb of the form
                   d1 ; ... ; d2 where d1, d2 have the same index.
                   then the kill set contains both d1 and d2.
                   the gen set contains only d2.
                 */

                for (Iterator itr = all_defs.iterator (); itr.hasNext (); )
                {
                    Def d = (Def) itr.next ();

                    if (d.get_position () == position)
                    {
                        Util.ASSERT (d.get_index () == index);

                        // remove any other def to the same index in the gen set
                        // so that only the last one remains.
                        // by induction, there can be at most one def to the same index
                        // in the _gen set.
                        // we break out as soon as we find this one def since
                        // can't continue iterating after removing the object
                        // from the collection (saw a ConcurrentModificationException)
                        for (Iterator gen_it = _gen.iterator (); gen_it.hasNext (); )
                        {
                            Def d1 = (Def) gen_it.next ();
                            if (d1.get_index () == d.get_index ())
                            {
                                _gen.remove (d1);
                                break;
                            }
                        }

                        _gen.add (d);
                        break;
                    }
                }
            }

            prev_begin = begin;
            begin = begin.getNext ();
        }

        for (Iterator it = _gen.iterator (); it.hasNext (); )
        {
            ( (RD_state) _out_state).add_def ( (Def) it.next ());
        }

        if (logger.isLoggable(Level.FINEST))
        {
	        logger.finest ("\nRDBB " + this);
	        logger.finest ("Gen: " + _gen);
	        logger.finest ("Kill: " + _kill);
        }
    }

    public RDBasicBlock (MethodGen mg, ConstantPoolGen cpgen,
                         LineNumberTable lnt, InstructionHandle ih, Class c)
    {
        super (mg, cpgen, lnt, ih, c);
        _gen = new LinkedHashSet ();
        _kill = new LinkedHashSet ();
    }

}
