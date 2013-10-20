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

public class RD_DF_algorithm
    extends DF_algorithm
{

    public static Logger parentLogger = Logger.getLogger("unifi");
    private static Logger _logger = Logger.getLogger("unifi.rd.RD_DF_algorithm");

    public ArrayList<Def> _all_defs = new ArrayList<Def> ();
    public ArrayList<Def> _arg_defs = new ArrayList<Def> ();
    public ArrayList<Use> _uses[];
    private int _n_arg_local_vars;
    private int _n_local_vars;

    //To store the set of definitions reaching each use of a variable
    public static Hashtable _def_list = new Hashtable ();

    public RD_DF_algorithm ()
    {
	try
	    {
		_bb_type = Class.forName ("unifi.rd.RDBasicBlock");
		_state_type = Class.forName ("unifi.rd.RD_state");
	    }
	catch (Exception e)
	    {
		Util.ASSERT (false);
	    }
    }

    public void init_for_method ()
    {
	_all_defs.clear ();
	_arg_defs.clear ();

	Type t[] = _current_mg.getArgumentTypes ();
	_logger.finer ("# of args = " + t.length);
	int lv_num = 0;
	int next_pos = -1;

	if (!_current_mg.isStatic ())
	    {
		Def d = new Def (lv_num, next_pos, false, false);
		_arg_defs.add (d);
		lv_num += 1;
		next_pos--;
	    }

	// create a set of init defs which contains dummy defs for each arg.
	for (int i = 0; i < t.length; i++)
	    {
		Def d = new Def (lv_num, next_pos, (t[i].getSize () == 2), false);
		_arg_defs.add (d);
		lv_num += t[i].getSize ();
		next_pos--;
	    }

	_n_arg_local_vars = lv_num;
	_n_local_vars = _current_mg.getMaxLocals ();
	System.out.println("Method max locals " + _n_local_vars);

	_all_defs.addAll (_arg_defs);

	// boundary condition: start_bb has method param defs reaching it
	for (Iterator it = _bb_collection.iterator (); it.hasNext (); )
	    {
		RDBasicBlock rdbb = (RDBasicBlock) it.next ();
		if (rdbb.is_start_bb ())
		    {
			RD_state rds = (RD_state) rdbb.get_out_state ();
			for (Iterator it1 = _arg_defs.iterator (); it1.hasNext (); )
			    {
				rds.add_def ( (Def) it1.next ());
				// _logger.fine ("Start RDBB found");
			    }
			break;
		    }
	    }

	// compute all_defs.
	for (Iterator it = _bb_collection.iterator (); it.hasNext (); )
	    {
		RDBasicBlock bb = (RDBasicBlock) it.next ();

		InstructionHandle begin = bb.get_begin_ih ();
		InstructionHandle end = bb.get_end_ih ();

		InstructionHandle prev_begin = null;

		while (prev_begin != end)
		    {
			Instruction insn = begin.getInstruction ();
			if (insn instanceof StoreInstruction || insn instanceof IINC)
			    {
				int index = 0, pos = 0;
				index = ( (IndexedInstruction) insn).getIndex ();
				pos = begin.getPosition ();

				boolean is_double = (insn.consumeStack (_current_cpgen) == 2);
				boolean is_iinc = insn instanceof IINC;
				Def d = new Def (index, pos, is_double, is_iinc);

				_all_defs.add (d);
			    }
			prev_begin = begin;
			begin = begin.getNext ();
		    }
	    }

	_logger.finest ("\nAll defs: " + _all_defs.size() + " defs found");
	for (Iterator it = _all_defs.iterator (); it.hasNext (); )
	    {
		_logger.finest (it.next().toString());
	    }

	// compute gen/kill sets
	_logger.fine ("\nComputing GEN/KILL sets");
	for (Iterator it = _bb_collection.iterator (); it.hasNext (); )
	    {
		RDBasicBlock rdbb = (RDBasicBlock) it.next ();
		rdbb.compute_gen_kill_sets (_all_defs);
	    }
    }

    /* returns a def if there exists one in the given set
       with index idx and bytecode position pos
       Aborts if def does not exist in the set.
    */
    private Def find_def (Collection s, int idx, int pos)
    {
	for (Iterator it = s.iterator (); it.hasNext (); )
	    {
		Def d = (Def) it.next ();
		if ( (d.get_index () == idx) && (d.get_position () == pos))
		    {
			return d;
		    }
	    }
	Util.ASSERT (false);
	return null;
    }

    private void remove_defs_with_index (Set s, int idx)
    {
	Set s1 = new HashSet ();
	for (Iterator it = s.iterator (); it.hasNext (); )
	    {
		Def d = (Def) it.next ();
		if (d.get_index () != idx)
		    {
			s1.add (d);
		    }
	    }

	s.clear ();
	s.addAll (s1);
    }

    /* Given a Collection of Use objects,
       merge their def sets which have at least one element in common.
       as a result, the uses which had common defs now point to
       the same def set.
    */
    private void unifi_sets_with_common_elements (Collection c)
    {
	for (Iterator it1 = c.iterator (); it1.hasNext (); )
	    {
		Use u1 = (Use) it1.next ();
		Set s1 = u1.get_def_set ();

		// for every set, compare with every other set
		for (Iterator it2 = c.iterator (); it2.hasNext (); )
		    {
			Use u2 = (Use) it2.next ();
			Set s2 = u2.get_def_set ();

			if (u2 == u1)
			    {
				continue;
			    }

			// just checking for intersection here
			Set temp = new HashSet ();
			temp.addAll (s1);
			temp.retainAll (s2);

			if (temp.size () >= 1)
			    {
				// _logger.fine ("merging the sets\ns1 = " + s1 + "\ns2 = " + s2);
				// they do intersect, merge the 2 sets
				s1.addAll (s2);
				u2.set_def_set (s1);
			    }
		    }
	    }
    }

    // given a set of defs (possible empty), returns a subset (possibly empty)
    // of defs which have the given index
    private Set subset_with_index (Set s, int idx)
    {
	Set s1 = new HashSet ();

	for (Iterator it = s.iterator (); it.hasNext (); )
	    {
		Def d = (Def) it.next ();
		if (d.get_index () == idx)
		    {
			s1.add (d);
		    }
	    }

	return s1;
    }

    @SuppressWarnings("unused")
    private void print_current_state ()
    {
	_logger.finest ("\nReaching defs are:");

	for (Iterator it = _bb_collection.iterator (); it.hasNext (); )
	    {
		RDBasicBlock rdbb = (RDBasicBlock) it.next ();

		_logger.finest ("For RDBB " + rdbb);
		_logger.finest ("IN:\n" + rdbb.get_in_state ());
		_logger.finest ("OUT:\n" + rdbb.get_out_state ());
	    }
    }

    @SuppressWarnings("unused")
    private void print_defs_for_each_use ()
    {
	for (int i = 0; i < _n_local_vars; i++)
	    {
		_logger.finest ("LV " + i + " (" +
				_uses[i].size () + ") uses");
		for (Iterator it = _uses[i].iterator (); it.hasNext (); )
		    {
			Use u = (Use) it.next ();
			int pos = u.get_position ();
			Set def_set = u.get_def_set ();
			_logger.finest ("Use at pos " + pos + ", reaching defs: " +
					def_set);
		    }
	    }
    }

    // checks that all pairs of def sets are either equal or
    // have no intersection
    // assert all sets have uniform values for is_double.
    private void verify_def_sets_equal_or_unique ()
    {
	for (int i = 0; i < _n_local_vars; i++)
	    {
		for (Iterator it = _uses[i].iterator (); it.hasNext (); )
		    {
			for (Iterator it1 = _uses[i].iterator (); it.hasNext (); )
			    {
				Use u = (Use) it.next ();
				Use u1 = (Use) it1.next ();
				Set defset = u.get_def_set ();
				Set defset1 = u1.get_def_set ();
				// either the sets are equal....
				if (defset1.equals (defset))
				    {
					continue;
				    }
				// ... or they have no intersection
				for (Iterator di = defset.iterator (); di.hasNext (); )
				    {
					Util.ASSERT (!defset1.contains (di.next ()));
				    }
			    }
		    }
	    }
    }

    // returns dead_defs (all defs which are not use)
    private Set get_dead_defs ()
    {
	// assume all defs are dead, and then start ruling out the
	// ones which aren't.

	Set dead_defs = new HashSet ();
	dead_defs.addAll (_all_defs);

	for (int i = 0; i < _n_local_vars; i++)
	    {
		for (Iterator it = _uses[i].iterator (); it.hasNext (); )
		    {
			Use u = (Use) it.next ();
			Set ds = u.get_def_set ();
			for (Iterator it1 = ds.iterator (); it1.hasNext (); )
			    {
				Def d = (Def) it1.next ();
				dead_defs.remove (d);
			    }
		    }
	    }

	return dead_defs;
    }

    /** returns a logical lv map which essentially contains a
     * bcp -> <physical,virtual> local var map
     */
    public Object get_answer ()
    {
	// this is a hashmap of pos -> uses
	_logger.fine (_n_arg_local_vars + " is the # of arg. local vars");
	_logger.fine (_n_local_vars + " is the # of total local vars");

	_uses = new ArrayList[_n_local_vars];
	// each use[i] is a list of uses of local var i
	for (int i = 0; i < _n_local_vars; i++)
	    {
		_uses[i] = new ArrayList<Use> ();

		/* go thru every use. what are the reaching defs ?
		   track current reaching defs starting with basic blocks in sets
		   and going thru insn list.
		*/

	    }
	for (Iterator it = _bb_collection.iterator (); it.hasNext (); )
	    {
		RDBasicBlock bb = (RDBasicBlock) it.next ();
		if (bb.is_start_bb ())
		    {
			continue;
		    }

		InstructionHandle current = bb.get_begin_ih ();
		InstructionHandle end = bb.get_end_ih ();
		Set reaching_def_set = ( (RD_state) (bb.get_in_state ())).get_set ();
		Set curr_def_set = reaching_def_set;
		if (_logger.isLoggable(Level.FINEST)) { _logger.finest ("at start, RD set size = " + curr_def_set.size()); }

		// curr_def_set will be updated as we step thru this BB
		do
		    {
			Instruction insn = current.getInstruction ();
			int pos = current.getPosition ();

			if (insn instanceof LoadInstruction || insn instanceof IINC)
			    {
				// is a use
				int idx = ( (IndexedInstruction) insn).getIndex ();
				boolean is_double = (insn.produceStack (_current_cpgen) ==
						     2);
				boolean is_iinc = (insn instanceof IINC);
				Use u = new Use (idx, pos, is_double, is_iinc);

				Set s = subset_with_index (curr_def_set, idx);
				Util.ASSERT (s.size() > 0);

				if (_logger.isLoggable(Level.FINEST)) 
				    { 
					_logger.finest ("subset with index " + idx + ", pos " + pos);
					for (Iterator it1 = s.iterator(); it1.hasNext(); ) { _logger.finest (it1.next().toString()); }
				    }
				// slight hack:
				// iinc are both def and use so the iinc def
				// needs to be in the same class as its reaching defs.
				// we achieve this by always including an iinc def in
				// it's own set of reaching defs.

				if (insn instanceof IINC)
				    {
					s.add (find_def (_all_defs, idx, pos));

					// all reaching defs must be the same size
				    }
				for (Iterator it1 = s.iterator (); it1.hasNext (); )
				    {
					Util.ASSERT ( ( (Def) it1.next ()).is_double () ==
						      is_double);

				    }
				u.set_def_set (s);
				_uses[idx].add (u);
			    }

			// note no else here, in case of iinc, we want both if's
			// to be executed.

			if (insn instanceof StoreInstruction || insn instanceof IINC)
			    {
				int idx = ( (IndexedInstruction) insn).getIndex ();
				// remove defs with same index from curr def set, and add this def
				remove_defs_with_index (curr_def_set, idx);
				Def d = find_def (_all_defs, idx, pos);
				curr_def_set.add (d);
			    }

			if (current == end)
			    {
				break;
			    }
			current = current.getNext ();
		    }
		while (true);
	    }

	// _logger.fine ("\nBefore collapsing def sets with common elements");
	// print_defs_for_each_use ();
	// now start collapsing all uses with non-null intersections.
	for (int i = 0; i < _n_local_vars; i++)
	    {
		unifi_sets_with_common_elements (_uses[i]);
		// _logger.fine ("\nAfter collapsing def sets with common elements");
		// print_defs_for_each_use ();

	    }
	verify_def_sets_equal_or_unique ();

	Set dead_defs = get_dead_defs ();

	// Now each def set needs to be assigned a unique number
	// we need to take care of a def without uses as well (dead def)
	LogicalLVMap lv_map = new LogicalLVMap ();
	ArrayList<Set<Def>> new_mapping = new ArrayList<Set<Def>> ();
	for (int i = 0; i < _n_local_vars; i++)
	    {
		new_mapping.add (null);
	    }
	int singles_added = 0, doubles_added = 0;

	for (int i = 0; i < _n_local_vars; i++)
	    {
		_logger.finer ("LV " + i + " (" + _uses[i].size () + ") uses");
		if (_uses[i].size () == 0)
		    {
			// this lv index has no uses.
			// do nothing - in the new lv_map, this lv num
			// retains its number, (though it's never used)
			_logger.finer ("Warning: No uses for var " + i); // not really a warning, happens all the time
			_logger.finer ( (_n_arg_local_vars <= i) ? " (Local var)" :
					" (Arg)");
			System.out.println("::: No use! :::");
			continue;
		    }

		for (Iterator it = _uses[i].iterator (); it.hasNext (); )
		    {
			Use u = (Use) it.next ();
			int pos = u.get_position ();
			int idx = u.get_index ();
			Set def_set = u.get_def_set ();
			int this_use_index = -1;
			boolean is_double = u.is_double ();

			// if the idx is not mapped in the new mapping
			// then no problem - just assign the same LV num
			// in the new mapping to this def_set
			if (new_mapping.get (idx) == null)
			    {
				Util.ASSERT (new_mapping.indexOf (def_set) == -1);
				new_mapping.set (idx, def_set);
				this_use_index = idx;
			    }
			else
			    {
				// otherwise see if this def set is already
				// assigned to some other LV index
				this_use_index = new_mapping.indexOf (def_set);
				if (this_use_index == -1)
				    {
					// if this def set has not been assigned an index
					// in the new LV mapping, create one

					// I used to think this was true but it is not
					// util.ASSERT (i >= _n_arg_local_vars);
					// e.g. the formal parameter for a method is
					// reassigned/updated.
					// multiple def sets shd happen only for real local
					// vars not for args

					new_mapping.add (def_set);
					if (is_double)
					    {
						new_mapping.add (def_set);
						doubles_added++;
					    }
					else
					    {
						singles_added++;
					    }
					this_use_index = new_mapping.indexOf (def_set);
					_logger.finest ("adding a " +
							(is_double ? "double" : "single") +
							" for index " + i +
							" the new index is " +
							this_use_index);
				    }
			    }

			lv_map.add (pos, i, this_use_index);
			
			System.out.println("::: "+pos+" ::: "+i+" :::logical::: "+this_use_index);//this_use_index);
			// must be at least one def in the def set
			// otherwise guaranteed (not potentially) uninit'ed variable is being used
			if (def_set.size () <= 0)
			    {
				_logger.severe ("Internal error: def_set size = " +
						def_set.size ());
				_logger.severe ("pos = " + pos + " i = " + i +
						" this_use_index = " + this_use_index);

				Util.ASSERT (false);
			    }

			// attach all defs for this use's def set to the same index in new mapping
			for (Iterator it1 = def_set.iterator (); it1.hasNext (); )
			    {
				Def d = (Def) it1.next ();
				Util.ASSERT (d.is_double () == is_double);
				lv_map.add (d.get_position (), i, this_use_index);
			    }
		    }
	    }

	_logger.finest ("Added " + singles_added + " single LVs, " +
			doubles_added + " double LVs");

	// assign dead defs starting from new_mapping.size()
	int dead_def_index = new_mapping.size ();
	_logger.finer (dead_defs.size () + " dead def(s) in this method ");
	_logger.finer (_n_local_vars + " local vars in this method ");
	_logger.finer (new_mapping.size () + " size of remap ");
	for (Iterator it = dead_defs.iterator (); it.hasNext (); )
	    {
		Def d = (Def) it.next ();
		//    if (d.get_position () < 0) // this is an unused arg
		//            {
		//               continue;
		//            }
		_logger.finer ("dead def : " + d);
		lv_map.add (d.get_position (), d.get_index (), dead_def_index++);
		// if (d.is_double ())
		//             {
		//                 lv_map.add (d.get_position (), d.get_index()+1, dead_def_index++);
		//             }
	    }
	return lv_map;
    }
}
