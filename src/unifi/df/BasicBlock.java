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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.apache.bcel.classfile.LineNumberTable;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.GOTO;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.Type;

import unifi.util.Util;

public class BasicBlock
{

    public InstructionHandle _begin_ih, _end_ih; // protected original, public for debug
    protected DF_state _in_state, _out_state;
    private Collection _succs, _preds;
    private int _entry_stack_height, _exit_stack_height;
    private MethodGen _mg;
    private ConstantPoolGen _cpgen;
    private LineNumberTable _lnt;

    private boolean _is_catch_block_entry_point;
    private boolean _is_jsr_entry_point; // true if target of a jsr
    private boolean _follows_jsr; // true if this bb is immediately after a bb which ends in a jsr
    private Collection _incoming_jsrs; // list of incoming jsr's if this is a jsr entry point.

    // immediately following or preceding BB's used only for
    // finding immediate successor of a bb ending in jsr and its
    // inverse
    private BasicBlock _following_bb, _preceding_bb;

    public int argCount;

    public BasicBlock (MethodGen mg, ConstantPoolGen cg, LineNumberTable lnt,
                       InstructionHandle begin_ih, Class state_class)
    {


        _mg = mg;
        _cpgen = cg;
        _lnt = lnt;
        _begin_ih = begin_ih;

        Type args_types[] = mg.getArgumentTypes();
        argCount = args_types.length;
        if( ! mg.isStatic() ) argCount++;

        try
        {
            _in_state = (DF_state) state_class.newInstance ();
            _out_state = (DF_state) state_class.newInstance ();
            /*
            _in_state.set_bb(this);
            _out_state.set_bb(this);
            */
        }
        catch (InstantiationException e)
        {
            Util.ASSERT (false);
        }
        catch (IllegalAccessException e)
        {
            Util.ASSERT (false);
        }

        _succs = new LinkedHashSet ();
        _preds = new LinkedHashSet ();
        _entry_stack_height = -1; // mark as invalid
        _is_catch_block_entry_point = false; // set to false by default
    }

    public boolean is_catch_block_entry_point () { return _is_catch_block_entry_point; }
    public void set_catch_block_entry_point () { _is_catch_block_entry_point = true; }

    ///////// jsr/ret stuff
    public void has_jsr_to (BasicBlock target_bb, BasicBlock following_bb)
    {
        target_bb._is_jsr_entry_point = true;
        if (target_bb._incoming_jsrs == null)
            target_bb._incoming_jsrs = new ArrayList();
        target_bb._incoming_jsrs.add (this);
        this._following_bb = following_bb;
        following_bb._preceding_bb = this;
        _following_bb._follows_jsr = true;
    }

    public boolean is_jsr_entry_point () { return _is_jsr_entry_point; }
    public Collection get_incoming_jsr_bbs () { return _incoming_jsrs; }

    public boolean follows_jsr () { return _follows_jsr; }
    public BasicBlock get_preceding_bb () { return _preceding_bb; }
    public BasicBlock get_following_bb () { return _following_bb; }

    ///////// end jsr/ret stuff

    public boolean is_start_bb ()
    {
        return (_begin_ih == null);
    }

    public LineNumberTable get_lnt () { return _lnt; }


    public void init ()
    {}

    public static void method_handle (MethodGen mg)
    {}

    public InstructionHandle get_begin_ih ()
    {
        return _begin_ih;
    }

    public InstructionHandle get_end_ih ()
    {
        return _end_ih;
    }

    /* height of stack in terms of # of words */
    public int get_entry_stack_height ()
    {
        return _entry_stack_height;
    }

    /* sets the entry stack height to x.
       if already set, simply checks that it's the same as x.
     */
    public void set_entry_stack_height (int x)
    {
        Util.ASSERT (x >= 0);

        if (_entry_stack_height < 0)
        {
            _entry_stack_height = x;
            _in_state.set_stack_height (_entry_stack_height);
        }
        else
        {
            if (_entry_stack_height != x)
            {
                System.out.println ("WARNING: Existing stack height = " +
                                    _entry_stack_height +
                                    ", trying to set it to " + x + "\nBB is:\n" + this);
                System.out.println ("Preds of this BB are:");
                Iterator it = _preds.iterator ();
                while (it.hasNext ())
                {
                    System.out.println ("------\n" + it.next ());
                }
                System.out.println ("Positions: start " + _begin_ih.getPosition () +
                                    " end " + _end_ih.getPosition ());
                Util.ASSERT (false);
            }
        }
    }

    public void set_end_ih (InstructionHandle end_ih)
    {
        Util.ASSERT (end_ih != null);
        _end_ih = end_ih;
        _exit_stack_height = _entry_stack_height;

        InstructionHandle ih = _begin_ih, processed_ih;

        do
        {
            Instruction insn = ih.getInstruction ();
            _exit_stack_height -= insn.consumeStack (_cpgen);
            Util.ASSERT (_exit_stack_height >= 0);
            _exit_stack_height += insn.produceStack (_cpgen);
            Util.ASSERT (_exit_stack_height >= 0);

            processed_ih = ih;
            ih = ih.getNext ();

        }
        while (processed_ih != end_ih);

        _out_state.set_stack_height (_exit_stack_height);
    }

    public DF_state get_in_state ()
    {
        return _in_state;
    }

    public DF_state get_out_state ()
    {
        return _out_state;
    }

    public void set_in_state (DF_state s)
    {
        Util.ASSERT (s != null);
        _in_state = s;
    }

    public void set_out_state (DF_state s)
    {
        Util.ASSERT (s != null);
        _out_state = s;
    }

    public MethodGen get_mg ()
    {
        return _mg;
    }

    public ConstantPoolGen get_cpgen ()
    {
        return _cpgen;
    }

    /* adds a pred or succ to a basic block. the pred/succ set is maintained
       as a set, so add_succ/add_pred can be called on the same basic block
       multiple times with the same argument */
    public void add_succ (BasicBlock b)
    { /* util.ASSERT (!this.has_succ(b)); */
        _succs.add (b);
    }

    public void add_pred (BasicBlock b)
    { /* util.ASSERT (!this.has_pred(b)); */
        _preds.add (b);
    }

    public int get_n_preds ()
    {
        return _preds.size ();
    }

    public int get_n_succs ()
    {
        return _succs.size ();
    }

    public Iterator get_pred_iterator ()
    {
        return _preds.iterator ();
    }

    public Iterator get_succ_iterator ()
    {
        return _succs.iterator ();
    }

    public boolean is_inited ()
    {
        return ( (_begin_ih != null) &&
                (_end_ih != null) &&
                (_entry_stack_height >= 0));
    }

    /* checks the relation:
       this has_pred bb
     */
    public boolean has_pred (BasicBlock bb)
    {
        for (Iterator it = get_pred_iterator (); it.hasNext (); )
        {
            if (it.next () == bb)
            {
                return true;
            }
        }

        return false;
    }

    /* checks the relation:
       this has_succ bb
     */
    public boolean has_succ (BasicBlock bb)
    {
        for (Iterator it = get_succ_iterator (); it.hasNext (); )
        {
            if (it.next () == bb)
            {
                return true;
            }
        }

        return false;
    }

    public void verify ()
    {
        Util.ASSERT (_entry_stack_height >= 0);

        if (_begin_ih != null)
        {
            Util.ASSERT (_begin_ih.getPosition () <= _end_ih.getPosition ());

        }
        for (InstructionHandle ih = _begin_ih;
             ih != null && ih != _end_ih;
             ih = ih.getNext ())
        {
            Instruction insn = ih.getInstruction ();
            Util.ASSERT (! (insn instanceof GOTO));
        }

//    util.ASSERT (this.is_inited());

        // no null preds or succs allowed
        // succ pred shd be correct bidir links
        for (Iterator it = get_pred_iterator (); it.hasNext (); )
        {
            BasicBlock bb = (BasicBlock) it.next ();
            Util.ASSERT (bb != null);
            Util.ASSERT (bb.has_succ (this));
            // stack heights will mismatch at catch block entry points
            if (this._is_catch_block_entry_point)
                Util.ASSERT (this._entry_stack_height == 1);
            else
                Util.ASSERT (this._entry_stack_height == bb._exit_stack_height);

        }

        for (Iterator it = get_succ_iterator (); it.hasNext (); )
        {
            BasicBlock bb = (BasicBlock) it.next ();
            Util.ASSERT (bb != null);
            Util.ASSERT (bb.has_pred (this));
            if (bb._is_catch_block_entry_point)
                Util.ASSERT (bb._entry_stack_height == 1);
            else
                Util.ASSERT (this._exit_stack_height == bb._entry_stack_height);
        }

        return;
    }

    public String toString ()
    {
        if (_begin_ih == null)
        {
            return "BB START";
        }
        else
        {
            return "BB [" + _begin_ih.getPosition () + ", " +
                _end_ih.getPosition () +
                "], entry/exit heights = " + _entry_stack_height + "/" +
                _exit_stack_height;
        }
    }

    public String toFullString ()
    {
        StringBuffer sb = new StringBuffer (this.toString ());

        sb.append (": Preds = [");
        for (Iterator it1 = get_pred_iterator (); it1.hasNext (); )
        {
            BasicBlock bb1 = (BasicBlock) it1.next ();
            InstructionHandle ih = bb1.get_begin_ih ();
            if (ih == null)
            {
                sb.append ("START ");
            }
            else
            {
                sb.append (ih.getPosition () + " ");
            }
        }

        sb.append ("] Succs = [");
        for (Iterator it1 = get_succ_iterator (); it1.hasNext (); )
        {
            BasicBlock bb1 = (BasicBlock) it1.next ();
            // get_begin_ih for a successor has to be non-null,
            // unlike for START in the pred case.
            sb.append (bb1.get_begin_ih ().getPosition () + " ");
        }
        sb.append ("]");

        return sb.toString ();
    }
}
