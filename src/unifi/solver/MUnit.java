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

class MUnit extends Unit {

String _name;
// boolean _is_dimension_less;

public MUnit (String s) { super (null); _name = s; }

// public boolean is_dimension_less() { return _is_dimension_less; }
// public void set_is_dimension_less(boolean b) { _is_dimension_less = b; }

public String toString() { return _name; }
public int hashCode() { return _name.hashCode(); }
public boolean equals(Object o) 
{ 
    return _name.equals (((MUnit) o)._name);
}
}

