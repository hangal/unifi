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

package unifi.units;


import org.apache.bcel.generic.*;


/** special class of elements of arrays created, when we dont have a unit for the original element */
public class ElementUnit extends Unit {

public ElementUnit(Type t) { super (t); }

public int hashCode() { return elementOf.hashCode() + 1; }

public boolean equals(Object o) { 
    if (!(o instanceof ElementUnit))
        return false;

    return elementOf.equals(((ElementUnit) o).elementOf); 
}

public String toString ()
{
    StringBuffer sb = new StringBuffer ( "Element of " + elementOf + " (cluster #" + elementOf.clusterNum + ")");
    appendAttrString (sb);
    return sb.toString();
}
}
