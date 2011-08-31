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

package unifi.solver;

import unifi.*;
import unifi.units.Unit;

class main {

public static void main (String args[])
{
    Unit a = new MUnit ("A");
    Unit b = new MUnit ("B");
    Unit c = new MUnit ("C");
    Unit d = new MUnit ("D");
    Unit e = new MUnit ("E");
    Unit f = new MUnit ("F");
    Unit g = new MUnit ("G");
    Unit h = new MUnit ("H");
    Unit i = new MUnit ("I");

    ConstraintSet cs = new ConstraintSet();

/*
    cs.add_constraint (a, b, c, 1);
    cs.add_constraint (a, b, b, 1);
    cs.add_constraint (b, d, e, 1);
    cs.add_constraint (b, e, b, 1);
*/
    // new ConstraintSolver().solve_constraints(cs);
}

}
