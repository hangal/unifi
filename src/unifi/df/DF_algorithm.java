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

package unifi.df;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.bcel.classfile.LineNumberTable;
import org.apache.bcel.generic.ATHROW;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.GOTO;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.JSR;
import org.apache.bcel.generic.JsrInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.RET;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.Select;
import org.apache.bcel.generic.StoreInstruction;

import unifi.unifi_state;
import unifi.util.Util;

/** abstract driver class for the dataflow framework. 
 * A concrete subclass implementation needs to only provide an "init_for_method" which
 * initializes state for the analysis of a new method. The rest of the dataflow algorithm is driven by this class, 
 * from the method analyze_method(). Subclasses have the option of overriding and making public a get_answer() method
 * which returns the results of the dataflow analysis.
 * currently supports dataflow analysis in the forward direction only.
 * the transfer functions and meet operators are defined in the DF_state objects associated with the particular subclass.
 * A concrete implementation of a dataflow algorithm has 3 parts:
 * A subclass of DF_algorithm consisting of init_for_method and get_answer
 * A subclass of DF_state which contains meet operators and transfer functions
 * A subclass of BasicBlock, typically tracking basic block properties relevant to the algorithm (e.g. for reaching defs, gen and kill sets)
 * @author hangal
 *
 */
abstract public class DF_algorithm
{
	private static boolean ignoreCatchBlocks = false;
	static
	{
	    String s = System.getProperty ("unifi.ignore.catch.blocks");
	    if ("true".equalsIgnoreCase (s))
	    {
	        System.out.println ("Ignoring catch blocks");
	        ignoreCatchBlocks = true;
	    }
	}
	
	private static Logger _logger = Logger.getLogger("unifi.dataflow.DF_algorithm");

    protected Class _bb_type; // subclasses can override this if they want a non-vanilla basic block type
    protected Class _state_type; // subclasses MUST override this
    protected Collection<BasicBlock> _bb_collection = new LinkedHashSet<BasicBlock> ();
    protected MethodGen _current_mg;
    protected ConstantPoolGen _current_cpgen;

    /* MUST be called, after _bb_collection is set up */
    protected abstract void init_for_method ();

    /* should be called at the end to read off the results,
       after data flow iteration has reached a fixed point
     */
    protected Object get_answer ()
    {
        return null;
    }

    public DF_algorithm ()
    {
        _bb_collection = new ArrayList<BasicBlock> ();
    }

    private BasicBlock new_bb (MethodGen mg, ConstantPoolGen cpgen, LineNumberTable lnt,
                               InstructionHandle ih)
    {
    	// would be cleaner if state creation was in concrete class's init_for_method() method...
    	// however, we still need a different BasicBlock for each algorithm.
        BasicBlock bb = null;
        Class arg_classes[] = new Class[5];

        try
        {
            arg_classes[0] = Class.forName ("org.apache.bcel.generic.MethodGen");
            arg_classes[1] = Class.forName ("org.apache.bcel.generic.ConstantPoolGen");
            arg_classes[2] = Class.forName ("org.apache.bcel.classfile.LineNumberTable");
            arg_classes[3] = Class.forName ("org.apache.bcel.generic.InstructionHandle");
            arg_classes[4] = Class.forName ("java.lang.Class");
        }
        catch (Exception e)
        {
            Util.ASSERT (false, "Failed to create argument objects for basic block constructor!\n" + e);
        }

        Constructor bb_constructor = null;
        
        try
        {
            if (_bb_type == null)
            	_bb_type = Class.forName ("unifi.df.BasicBlock"); // default BB
            bb_constructor = _bb_type.getConstructor (arg_classes);
        }
        catch (Exception e)
        {
            Util.ASSERT (false);
        }

        Object args[] = new Object[5];
        args[0] = mg;
        args[1] = cpgen;
        args[2] = lnt;
        args[3] = ih;
        args[4] = _state_type;

        try
        {
            bb = (BasicBlock) bb_constructor.newInstance (args);
        }
        catch (Exception e)
        {
            Util.ASSERT (false, "Exception occurred while trying to create a BasicBlock object: " + e);
        }

        _bb_collection.add (bb);
        return bb;
    }

    /* creates a new bb for the given ih, if it doesn't already exist.
       uses ih_hash as the hashmap of all start ih->bb
       if a new bb is created, it is added to _bb_collection
       NB: if a bb is created, returns it, or returns null if the bb
       was already existing.
     */

    private BasicBlock mark_bb_entry_point (MethodGen mg, ConstantPoolGen cpgen,
                                            LineNumberTable lnt,
                                            InstructionHandle ih,
                                            Map<InstructionHandle, BasicBlock> ih_hash)
    {
        BasicBlock bb = ih_hash.get (ih);
        if (bb == null)
        {
            bb = new_bb (mg, cpgen, lnt, ih);
            if (ih != null)
            {
                ih_hash.put (ih, bb);
            }
            return bb;
        }
        else
        {
            return null;
        }
    }

     /* finds all basic block entry points in mg, creates basic blocks for them,
      * puts all the basic blocks in _bb_collection and creates a hashmap of
       instruction handle -> basic block object (which contains all instructions
       which begin a bb)
       a bb is identified by its starting insn-handle
     */
    private void discover_basic_blocks (MethodGen mg, ConstantPoolGen cpgen,
                                        LineNumberTable lnt, Map<InstructionHandle, BasicBlock> ih_hash)
    {
        Util.ASSERT (ih_hash != null);
        Util.ASSERT (_bb_collection != null);

        InstructionList il = mg.getInstructionList ();

        // first identify all basic block entry points

        InstructionHandle first_ih = il.getStart ();
        Util.ASSERT (first_ih != null);

        // the start BB does not look up the ih_hash.
        BasicBlock start_bb = new_bb (mg, cpgen, lnt, null);
        Util.ASSERT (start_bb != null);
        start_bb.set_entry_stack_height (0);

        BasicBlock first_bb = mark_bb_entry_point (mg, cpgen, lnt, first_ih, ih_hash);
        Util.ASSERT (first_bb != null);
        first_bb.set_entry_stack_height (0);

        start_bb.add_succ (first_bb);
        first_bb.add_pred (start_bb);

        Stack<BasicBlock> found_bbs = new Stack<BasicBlock> ();
        found_bbs.push (first_bb);

        if (!ignoreCatchBlocks)
        {
            CodeExceptionGen[] ex_gen = mg.getExceptionHandlers ();
            //System.out.println (ex_gen.length + " Exception handlers");
            for (int i = 0; i < ex_gen.length; i++)
            {
                InstructionHandle ex_start_ih = ex_gen[i].getHandlerPC ();
                BasicBlock ex_bb = mark_bb_entry_point (mg, cpgen, lnt, ex_start_ih,
                    ih_hash);
                // ex_bb could be null, i.e it could be pre-existing
                // in the bb list. I found this happening due to some
                // finally clause's (see bytecode for unifi.jedit.JEditTextArea.setText() for example
                if (ex_bb != null)
                {
                    ex_bb.set_catch_block_entry_point();
                    ex_bb.set_entry_stack_height (1);
                    found_bbs.push (ex_bb);
                }
            }
        }

        Map<InstructionHandle, List<InstructionHandle>> targetToJsrs = computeTargetToJSRsMap();

        while (found_bbs.size () > 0)
        {
        	BasicBlock current_bb = found_bbs.pop ();
        	// System.out.println ("processing bb " + current_bb.get_begin_ih ());

        	current_bb_loop:
        		for (InstructionHandle ih = current_bb.get_begin_ih ();
        		ih != null;
        		ih = ih.getNext ())
        		{
        			Instruction insn = ih.getInstruction ();

        			if ( (insn instanceof BranchInstruction) ||
        					(insn instanceof ATHROW) ||
        					(insn instanceof ReturnInstruction) ||
        					(insn instanceof RET))
        			{
        				if (insn instanceof Select)
        				{
        					InstructionHandle targets[] = ((Select) insn).getTargets ();
        					for (int i = 0; i < targets.length; i++)
        					{
        						BasicBlock new_bb = mark_bb_entry_point (mg, cpgen, lnt,
        								targets[i], ih_hash);
        						if (new_bb != null)
        							found_bbs.push (new_bb);
        					}
        					InstructionHandle defaultTarget = ( (Select) insn).getTarget();
        					BasicBlock new_bb = mark_bb_entry_point (mg, cpgen, lnt, defaultTarget, ih_hash);
        					if (new_bb != null)
        						found_bbs.push (new_bb);
        				}
        				else if (insn instanceof RET)
        				{
        					InstructionHandle start = getFirstIHOfSub(ih);
        					List<InstructionHandle> list = targetToJsrs.get(start);
        					for (InstructionHandle ih1: list)
        					{
        						BasicBlock new_bb = mark_bb_entry_point (mg, cpgen, lnt, ih1.getNext() , ih_hash);
        						if (new_bb != null)
        							found_bbs.push (new_bb);
        					}
        				}
        				else if (insn instanceof BranchInstruction)
        				{
        					InstructionHandle binsn_target = ( (BranchInstruction) insn).getTarget ();
        					BasicBlock new_bb = mark_bb_entry_point (mg, cpgen, lnt, binsn_target, ih_hash);
        					if (new_bb != null)
        					{
        						found_bbs.push (new_bb);

        						// if there's a chance of a fall thru, i.e. not a goto
        						// add a bb for the next insn as well.
        					}

        					if ((!(insn instanceof GOTO)) && (!(insn instanceof JsrInstruction)))
        					{
        						if (ih.getNext () != null)
        						{
        							new_bb = mark_bb_entry_point (mg, cpgen, lnt, ih.getNext (), ih_hash);
        							if (new_bb != null)
        							{
        								found_bbs.push (new_bb);
        							}
        						}
        					}
        				}
        				// else do nothing for athrow, return, ret

        				break current_bb_loop;
        			}
        		}
        }
    }

    // Returns a map ih -> list, mapping an ih to all all Jsrs pointing to it
    private Map<InstructionHandle, List<InstructionHandle>> computeTargetToJSRsMap()
    {
    	Map<InstructionHandle, List<InstructionHandle>> ihToJSR = new LinkedHashMap<InstructionHandle, List<InstructionHandle>>();
    	InstructionHandle[] allIhs = _current_mg.getInstructionList().getInstructionHandles();

    	for (InstructionHandle ih: allIhs)
    	{
    		Instruction insn = ih.getInstruction();
    		if (insn instanceof JsrInstruction)
    		{
    			JsrInstruction jsr = (JsrInstruction) insn;
    			InstructionHandle jsrTarget = jsr.getTarget();

    			List<InstructionHandle> list = ihToJSR.get(jsrTarget);
    			if (list == null)
    			{
    				list = new ArrayList<InstructionHandle>();
    				ihToJSR.put(jsrTarget, list);
    			}
    			list.add(ih);
    		}
    	}

    	return ihToJSR;
    }

    // Returns the ih which is the beginning of the jsr sub that ends with the ret at endIH
    private InstructionHandle getFirstIHOfSub(InstructionHandle retIH)
    {
    	Instruction retInsn = retIH.getInstruction();
    	Util.ASSERT(retInsn instanceof RET);
    	RET ret = (RET) retInsn;
    	Map<InstructionHandle, List<InstructionHandle>> jsrMap = computeTargetToJSRsMap();
    	int retIdx = ret.getIndex();

    	// simplifying assumption: we scan backwards from retIH, assume the ret block is straight line.
    	InstructionHandle ih = retIH.getPrev();
    	while (ih != null)
    	{
    		List<InstructionHandle> jsrList = jsrMap.get(ih);
    		// check if any jsr points to ih
    		if (jsrList != null)
    		{
    			// this ih is the target of a jsr all right (we don't care which one)
    			// the first insn at the target of a jsr must be a store of the PC
    			// check whether the PC local var index is the same as that of the ret
    			Instruction insn = ih.getInstruction();
    			if (retIdx == ((StoreInstruction) insn).getIndex())
    				return ih;
    		}
    		// keep looking backwards
    		ih = ih.getPrev();
    	}

    	Util.fatal("Hey, no subroutine beginning for the block ending at " + retIH + " \nIs this weird or what ? Happy debugging!");
    	return null;
    }

    /* sets the succ of current_bb to the bb starting with succ_ih.
       ih_hash is the usual hashmap of starting insn handle -> bb's
       stack height is what the entry stack height of the succ bb is
       to be set to.
         if the succ bb is still uninited, it is pushed onto the uninited_bbs stack.
     */
    private void found_succ (Stack<BasicBlock> uninited_bbs, Map<InstructionHandle, BasicBlock> ih_hash,
                             int stack_height,
                             BasicBlock current_bb, InstructionHandle succ_ih)
    {
        BasicBlock bb = ih_hash.get (succ_ih);
        Util.ASSERT (bb != null);
        current_bb.add_succ (bb);
        bb.add_pred (current_bb);
        bb.set_entry_stack_height (stack_height);

        if (!bb.is_inited () && !uninited_bbs.contains (bb))
        {
            uninited_bbs.push (bb);
        }
    }

    private InstructionHandle start_ih_of_bb_ending_with_ih (Map<InstructionHandle, BasicBlock> ih_hash, InstructionHandle end_ih)
    {
        for (Iterator it = ih_hash.values().iterator(); it.hasNext(); )
        {
            BasicBlock bb = (BasicBlock) it.next();
            if (end_ih.equals(bb.get_end_ih()))
                return bb.get_begin_ih();
        }

        // if we don't find a bb which ends at end_ih, we scroll forward in the instruction list
        // till we do find a handle which ends a bb.
        return start_ih_of_bb_ending_with_ih (ih_hash, end_ih.getNext());
    }

    /* sets up basic blocks completely, including preds/succs, entry_stack_height and
       end_ih
       _bb_collection is the collection of all basic blocks
         ih_hash is a map of insn handle-> basic block, defined for all insn handles
       which start a bb
     */
    private void initialize_basic_blocks (MethodGen mg, ConstantPoolGen cpgen,
                                          Map<InstructionHandle, BasicBlock> ih_hash)
    {
        InstructionList il = mg.getInstructionList ();
        BasicBlock first_bb = ih_hash.get (il.getStart ());
        Util.ASSERT (first_bb != null);

        Stack<BasicBlock> uninited_bbs = new Stack<BasicBlock> ();

        first_bb.set_entry_stack_height (0);
        uninited_bbs.push (first_bb);

        if (!ignoreCatchBlocks)
        {
            CodeExceptionGen[] ex_gen = mg.getExceptionHandlers ();
            for (int i = 0; i < ex_gen.length; i++)
            {
                InstructionHandle ex_start_ih = ex_gen[i].getHandlerPC ();
                BasicBlock ex_bb = ih_hash.get (ex_start_ih);
                Util.ASSERT (ex_bb != null);

                // catch blocks always have an entry stack height of 1
                ex_bb.set_entry_stack_height (1);
                // add the ex block to the uninited bb's list only if it
                // already doesn't exist. It's possible for the same block to
                // exist multiple times e.g. because of finally clauses
                if (!uninited_bbs.contains(ex_bb))
                    uninited_bbs.push (ex_bb);
            }
        }

        Map<InstructionHandle, List<InstructionHandle>> targetToJsrs = computeTargetToJSRsMap();

        while (uninited_bbs.size () > 0)
        {
            BasicBlock current_bb = uninited_bbs.pop ();
//            System.out.println("start bb = " + current_bb.get_begin_ih());
            if (current_bb.get_end_ih() != null)
            Util.ASSERT (current_bb.get_end_ih () == null);

            Util.ASSERT (!current_bb.is_inited ());
            InstructionHandle begin_ih = current_bb.get_begin_ih ();
            int stack_height = current_bb.get_entry_stack_height ();

            per_bb_loop:
            for (InstructionHandle end_ih = begin_ih; end_ih != null;
                 end_ih = end_ih.getNext ())
            {
                //System.out.println("####### "+end_ih);
                // look for this bb's end_ih
                // this can happen either due to:
                // this is the last ih in this method
                // an ih in this BB is known to start a basic block (need stack height before update)
                // a branch instruction is encountered in this bb (need stack height after update)
                // an athrow instruction is encountered
                Instruction end_insn = end_ih.getInstruction ();

                if (begin_ih != end_ih)
                {
                    // don't want to do this on the first insn in the bb
                    BasicBlock end_ih_bb = ih_hash.get (end_ih);
                    if (end_ih_bb != null)
                    {
                        current_bb.set_end_ih (end_ih.getPrev ());
                        found_succ (uninited_bbs, ih_hash, stack_height,
                                    current_bb, end_ih);
                        break per_bb_loop;
                    }
                }

                stack_height -= end_insn.consumeStack (cpgen);
                // even after consumption, stack height shd be >= 0
                Util.ASSERT (stack_height >= 0);
                stack_height += end_insn.produceStack (cpgen);

                if ( (end_insn instanceof BranchInstruction) ||
                    (end_insn instanceof ATHROW) ||
                    (end_insn instanceof ReturnInstruction) ||
                    (end_insn instanceof RET))
                {
                    // must set end ih first, before calling found_succ because that in turn
                    // calls is_inited, which will check on whether end_ih is set

                    current_bb.set_end_ih (end_ih);

                    // now mark all the successors
                    if (end_insn instanceof Select)
                    {
                        InstructionHandle targets[] = ((Select) end_insn).getTargets ();
                        for (int i = 0; i < targets.length; i++)
                            found_succ (uninited_bbs, ih_hash, stack_height, current_bb, targets[i]);
                        InstructionHandle defaultTarget = ((Select) end_insn).getTarget();
                        found_succ (uninited_bbs, ih_hash, stack_height, current_bb, defaultTarget);
                    }
                    else if (end_insn instanceof RET)
                    {
                    	// set up the next insn of the jsrs as successors of this ret
                    	InstructionHandle start = getFirstIHOfSub(end_ih);
                    	List<InstructionHandle> list = targetToJsrs.get(start);
                    	for (InstructionHandle ih : list)
                    		found_succ (uninited_bbs, ih_hash, stack_height, current_bb, ih.getNext() );
                    }
                    else if (end_insn instanceof BranchInstruction)
                    {
                        BranchInstruction bi = (BranchInstruction) end_insn;
                        found_succ (uninited_bbs, ih_hash, stack_height,
                                    current_bb, bi.getTarget ());

                        int fall_thru_stack_height = stack_height;
                        // fall thru is special for jsr because when it returns,
                        // stack height will be one less than what we currently computed

                        // we stop considering that a JSR ever falls thru
                        // therefore we currently miss some pred-succ relations
                        // between the target of the JSR and the following instruction
                        // where control returns after the ret

                        if ((!(end_insn instanceof GOTO)) && (!(end_insn instanceof JsrInstruction)))
                        {
                            if (end_ih.getNext () != null)
                            {
                                found_succ (uninited_bbs, ih_hash,
                                            fall_thru_stack_height, current_bb,
                                            end_ih.getNext ());
                            }
                        }
                    }

                    // for athrow, returns and ret's do nothing
                    break per_bb_loop;
                }
                else if (end_ih.getNext () == null)
                { // think it shd never come here
                    current_bb.set_end_ih (end_ih);
                }
            }
        }

        // we add the catch block as a successor to the bb which ends at the
        // same endPC as the catch's try.
        if (!ignoreCatchBlocks)
        {
            CodeExceptionGen[] ex_gen = mg.getExceptionHandlers ();
            for (int i = 0; i < ex_gen.length; i++)
            {
                InstructionHandle ex_start_ih = ex_gen[i].getHandlerPC ();

                // there are some funny catch blocks around monitor enter
                // which are catches for themselves.
                // see e.g. http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4414101
                // we need to ignore these, otherwise they cause problems later
                // when we try to find a bb which ends at the end of the caught range.
                if (ex_gen[i].getStartPC() == ex_start_ih)
                    continue;
                BasicBlock ex_bb = ih_hash.get (ex_start_ih);
                Util.ASSERT (ex_bb != null);

                // System.out.println ("exgen end_PC " + ex_gen[i].getEndPC());
                // System.out.println ("ex start_ih end_PC " + ex_start_ih);
                // print_collection();

                // the endPC given to us by BCEL is the end PC of the last covered insn,
                // it doesn't include the goto which is the actual last insn of the bb.
                // therefore we look for the bb which ends in endPC's next insn handle.
                // update: sometimes (Esp. for a block ending in athrow, the endPC itself
                // is correct)
                InstructionHandle endPC = ex_gen[i].getEndPC();
                InstructionHandle ih = start_ih_of_bb_ending_with_ih(ih_hash, endPC);
                BasicBlock pred_of_ex_bb = ih_hash.get(ih);

                // mark the catch block a successor of that basic block.
                found_succ (uninited_bbs, ih_hash, 1, pred_of_ex_bb, ex_start_ih);
            }
        }
    }

    public void print_collection ()
    {
        verify_collection ();

        if (_logger.isLoggable(Level.FINE))
        {
	        _logger.fine ("Listing all basic blocks in method: " + _bb_collection.size () + " blocks");
	        for (Iterator it = _bb_collection.iterator (); it.hasNext (); )
	        {
	            BasicBlock bb = (BasicBlock) it.next ();
	            _logger.fine (bb.toFullString ());
	        }
        }
    }

    protected void verify_collection ()
    {
        for (Iterator it = _bb_collection.iterator (); it.hasNext (); )
        {
            BasicBlock bb = (BasicBlock) it.next ();
            bb.verify ();
        }
    }

    /* marks jsr entry points in the bb collection.
     * each jsr entry point is marked and the list of bb's
     * to which it belongs.
     */
    public void mark_jsr_entry_points(Map<InstructionHandle, BasicBlock> ih_hash)
    {
        for (Iterator it = _bb_collection.iterator(); it.hasNext(); )
        {
            BasicBlock bb = (BasicBlock) it.next();
            if (bb.is_start_bb())
                continue;

            InstructionHandle end_ih = bb.get_end_ih();

            if (end_ih.getInstruction() instanceof JSR)
            {

                InstructionHandle jsr_ih = end_ih;
                InstructionHandle target_ih = ((JSR) jsr_ih.getInstruction()).getTarget();

                BasicBlock target_bb = ih_hash.get(target_ih);
                Util.ASSERT (target_bb != null);

                InstructionHandle ih_following_jsr = jsr_ih.getNext();
                // we don't expect the last insn in a method to be a jsr
                Util.ASSERT (ih_following_jsr != null);

                BasicBlock bb_following_jsr = ih_hash.get (ih_following_jsr);
                Util.ASSERT (bb_following_jsr != null);

                bb.has_jsr_to (target_bb, bb_following_jsr);
            }
        }
    }

    public Object analyze_method (MethodGen mg, ConstantPoolGen cpgen, LineNumberTable lnt)
    {
        _current_mg = mg;
        _current_cpgen = cpgen;

        // map of insn handle -> basic block which the ih starts
        Map<InstructionHandle, BasicBlock> ih_hash = new LinkedHashMap<InstructionHandle, BasicBlock> ();

        _bb_collection.clear ();

        discover_basic_blocks (mg, cpgen, lnt, ih_hash);
        initialize_basic_blocks (mg, cpgen, ih_hash);
        mark_jsr_entry_points(ih_hash);

        verify_collection ();
        print_collection ();

        for (Iterator itr = _bb_collection.iterator (); itr.hasNext (); )
        {
            BasicBlock bb = (BasicBlock) itr.next ();
            bb.verify ();
            bb.init ();
        }

        // compute the out state for all BB's once using the transfer function
        // System.out.println ("# BBs = " + _bb_collection.size ());
        init_for_method ();
        for (Iterator itr = _bb_collection.iterator (); itr.hasNext (); )
        {
            BasicBlock bb = (BasicBlock) itr.next ();
            if (bb.is_start_bb ())
            {
                continue;
            }
            DF_state s_in = bb.get_in_state ();
            s_in.transfer (bb);
        }

        // now loop till fixed point
        boolean fixed_point = true;
        do
        {
            fixed_point = true;
            for (Iterator itr = _bb_collection.iterator (); itr.hasNext (); )
            {
                // meet this bb's in state with all it's pred's out states

                BasicBlock bb = (BasicBlock) itr.next ();

//                System.out.println (" Processing " + bb);

                // we DONT' want processing for start bb
                // otherwise it destroys START's out state
                // which should not be touched.
                if (bb.is_start_bb ())
                {
                    continue;
                }

                DF_state s_in = bb.get_in_state ();
                DF_state old_s_in = s_in.create_copy ();
                if (s_in instanceof unifi_state)
                {
                    Util.ASSERT ( ( (unifi_state) s_in)._stack.size () ==
                                 ( (unifi_state) old_s_in)._stack.size ());
                }
                s_in.clear ();

                for (Iterator pred_itr = bb.get_pred_iterator ();
                     pred_itr.hasNext (); )
                {
                    BasicBlock next_pred = (BasicBlock) pred_itr.next ();
                    next_pred.verify ();
                    s_in.meet (next_pred.get_out_state (), bb, old_s_in);
                }

                boolean change = ! (s_in.equals (old_s_in));
                if (change)
                {
                    // ensure that hte change is ok (state doesn't decrease when
                    // it's meant to increase etc). this catches infinite loop
                    // type errors in the DF iteration.
                    //
                     s_in.verify_against_old_state (old_s_in, bb);

                    fixed_point = false;
                    s_in.transfer (bb);
                }
            }
        }
        while (!fixed_point);

        return get_answer ();
    }
}
