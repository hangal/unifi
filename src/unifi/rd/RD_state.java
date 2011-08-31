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

import unifi.df.*;
import unifi.util.Util;

import org.apache.bcel.generic.*;

public class RD_state
    extends DF_state
{

    private static Logger _logger = Logger.getLogger("unifi.RD_state");

    // shd be private, temporarily set to public for debugging.
    public Set _set;

    public RD_state ()
    {
        _set = new LinkedHashSet ();
    }

    public void clear ()
    {
        _set.clear ();
    }

    public DF_state create_copy ()
    {
        RD_state r = new RD_state ();
        r._set.addAll (_set);
        return r;
    }

    /* add's the def d to the set of reaching defs in this state */
    public void add_def (Def d)
    {
        _set.add (d);
    }


    /** the meet is messy because of jsr's.
     * it needs to know the bb at whose input the meet is happening (this_bb),
     * as well as that bb's original in_state (if any)
     */
    public void meet (DF_state s, BasicBlock this_bb, DF_state this_bb_orig_in_state)
    {
    	// this_bb must be bb at whose input we are computing this state
    	Util.ASSERT(this_bb.get_in_state() == this);

        RD_state other_state = (RD_state) s;

        // simple union if this_bb doesn't follow a jsr
        if (!this_bb.follows_jsr())
        {
            ( (HashSet) _set).addAll ( other_state._set);
            return;
        }

        // if this bb is one just following a jsr X, we have to be careful.
        // we don't merge all def's in s.

        // 8/8/2010: we kill the def d iff:
        // some other incoming jsr's outset to the same target contains d
        // my predecessor doesn't contain d
        // *my* incoming set doesn't contain d
        // jiwon found a case (javacal) where the def was incorrectly killed because
        // other_jsr_bb happened to be an exception catch block for this bb.
        // a def from the jsr handler flowed to this bb, then to other_jsr_bb (via its catch handler)
        // and therefore back to the jsr handler. we thought the def came from the other_jsr_bb and
        // killed the def for this bb's incoming set, which incorrectly got smaller!

    	// my_jsr_bb is the bb ending in the jsr just preceding this_bb
    	// so all of my_jsr_bb's defs should flow into this bb
        BasicBlock my_jsr_bb = this_bb.get_preceding_bb();
        Util.ASSERT (my_jsr_bb != null);

        // get the jsr target
        InstructionHandle end_ih = my_jsr_bb.get_end_ih();
        Instruction insn = end_ih.getInstruction();
        Util.ASSERT (insn instanceof JSR);
        JSR jsr_insn = (JSR) insn;
        InstructionHandle jsr_target_ih = jsr_insn.getTarget();

        // look for the jsr_target_bb
        BasicBlock jsr_target_bb = null;
        for (Iterator it = my_jsr_bb.get_succ_iterator(); it.hasNext(); )
        {
            BasicBlock next = (BasicBlock) it.next();
            if (jsr_target_ih == next.get_begin_ih())
            {
                jsr_target_bb = next;
                Util.ASSERT (jsr_target_bb.is_jsr_entry_point());
                break;
            }
        }
        Util.ASSERT (jsr_target_bb != null);

        // find all bb's that jsr to this jsr target
        Collection bbs_to_jsr_target = jsr_target_bb.get_incoming_jsr_bbs();
        Util.ASSERT (bbs_to_jsr_target.contains(my_jsr_bb));


        if (_logger.isLoggable(Level.FINE))
        {
        	_logger.fine ("Computing jsr defs: this_bb = " + this_bb + "my_jsr = " + my_jsr_bb + " jsr handler = " + jsr_target_bb);
            _logger.fine ("for jsr handler block: " + jsr_target_bb + ", predecessor blocks are:");
            for (Iterator it1 = bbs_to_jsr_target.iterator(); it1.hasNext();)
                _logger.fine ("  " + it1.next());
            _logger.fine ("for jsr handler block: " + jsr_target_bb + ", successor blocks are:");
            for (Iterator it1 = jsr_target_bb.get_succ_iterator(); it1.hasNext();)
                _logger.fine ("  " + it1.next());
        }

        Set my_jsr_bb_out_set = ((RD_state) my_jsr_bb.get_out_state())._set;
        Set bb_orig_in_set = null;
        if (this_bb_orig_in_state != null)
        	bb_orig_in_set = ((RD_state) this_bb_orig_in_state)._set;

        outer:
        for (Iterator it = other_state._set.iterator(); it.hasNext(); )
        {
            Def d = (Def) it.next();

            boolean definitely_add_d = false;
            if (my_jsr_bb_out_set.contains(d) ||
            	((bb_orig_in_set != null) && bb_orig_in_set.contains(d)))
            {
            	definitely_add_d = true;
            }

            // UPDATE: below check is probably redundant - sgh, aug 8th '10
            // the def at jsr_insn.getPosition() which is the astore of the PC
            // always reaches. not exempting this from the check was causing a problem
            // in tomcat/jakarta-apache-5.5.5/common/lib/jasper-compiler-jdt.jar!org/eclipse/jdt/internal/compiler/SourceElementParser.class
            // the def at the jsr insn reaches bb in the first iteration but is
            // later removed because it also reaches the outset of some other jsr
            // to the same jsr block. this destroys the invariant that reaching def sets
            // should only increase.

            if (d.get_position() == jsr_target_bb.get_begin_ih().getPosition())
            	definitely_add_d = true;

            if (definitely_add_d)
            {
            	_set.add(d);
            	continue;
            }

            // check if this def is contained in any of the other incoming_jsr_bbs.
            // if not, we'll add it to the def set for this bb.
            //
            for (Iterator it1 = bbs_to_jsr_target.iterator(); it1.hasNext();)
            {
                BasicBlock other_jsr_bb = (BasicBlock) it1.next();
                if (other_jsr_bb == my_jsr_bb)
                    continue;

                // if d is present in the out state of any incoming_jsr_bb
                // then this def doesn't flow to bb
                Set other_jsr_out_set = ((RD_state) other_jsr_bb.get_out_state())._set;

                if (other_jsr_out_set.contains(d))
                {
                    _logger.fine ("killing def " + d + " it comes to " + this_bb + " from incoming jsr bb " + other_jsr_bb);
                    continue outer;
                }
            }
            _set.add (d);
            }
    }

// OUT(B) = IN(B) - KILL(B) U GEN(B)
    public void transfer (BasicBlock bb)
    {
        if (bb.is_start_bb ())
        {
            return;
        }

        RDBasicBlock rdbb = (RDBasicBlock) bb;
        Set in = ( (RD_state) rdbb.get_in_state ())._set;
        Set out = ( (RD_state) rdbb.get_out_state ())._set;

        out.clear ();

        out.addAll (in); // OUT(B) = IN (B)
        out.removeAll (rdbb.get_kill ()); // OUT(B) = OUT(B) - KILL(B)
        out.addAll (rdbb.get_gen ()); // OUT(B) = OUT(B) U GEN(B)
    }

    public Set get_set ()
    {
        return _set;
    }

    public void set_stack_height (int i)
    { /* */ }

    public void add (Object o)
    {
        _set.add (o);
    }

    public int hashCode ()
    {
	return _set.hashCode();

    }

    public boolean equals (Object s)
    {
        RD_state other = (RD_state) s;
        return _set.equals (other._set);
    }

    // sets should only increase, never decrease
    // error if a set is smaller than the old state
    public void verify_against_old_state (DF_state old_state, BasicBlock bb)
    {
        if (_set.size() < ((RD_state) old_state)._set.size())
        {
            System.out.println ("ERROR: state verification failed for bb " + bb);
            System.out.println ("old state " + old_state);
            System.out.println ("new state " + this);
            // could do a more elaborate check here to ensure that
            // all elements _old_state._set are contained in _set
            Util.ASSERT (false);
        }
    }

    public String toString ()
    {
        StringBuffer sb = new StringBuffer ();
        sb.append (_set.size() + " elements\n");
        for (Iterator it = _set.iterator (); it.hasNext (); )
        {
            sb.append (it.next () + "\n");
        }
        return sb.toString ();
    }

}
