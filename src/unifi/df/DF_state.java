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

abstract public class DF_state implements Cloneable
{
    abstract public void set_stack_height (int i);

    /* applies the meet operator between 'this' and other,
       and updates this accordingly. bb is the basic block
       at the entrance to which this is happening.
       bb_orig_in_state can be null and should be the original in-state of bb before this meet happens
       (this param is required only for jsr corner cases)
     */
    abstract public void meet (DF_state other, BasicBlock bb, DF_state bb_orig_in_state);

    /* applies the transfer function thru the given basic block to
      'this', and updates this accordingly.
     */
    abstract public void transfer (BasicBlock bb);

// clear the state contained in this state element,
// in preparation for recomputing it fresh by performing
// a meet on all predecessors
    abstract public void clear ();

    abstract public DF_state create_copy ();

    abstract public void verify_against_old_state (DF_state old_state, BasicBlock bb);

    /*
    private BasicBlock _bb; // the bb this state object belongs to
    public BasicBlock get_bb() { return _bb; }
    public void set_bb(BasicBlock bb) { _bb = bb; }
    */
}
