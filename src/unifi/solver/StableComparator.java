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

import java.util.*;

import unifi.units.Unit;


// comparator which takes a collection of preferred units
// and orders any preferred unit before any non-preferred unit.
// preferred units may be null in which case the compare always returns -1  (is this right ?)
public class StableComparator<T> implements Comparator {

Collection preferredUnits;

public StableComparator(Collection c)
{
    preferredUnits = c;
}

public int compare (Object o1, Object o2)
{
    if (o1 == o2) return 0;

    if (preferredUnits != null) 
    {

        if (preferredUnits.contains (o1) && !preferredUnits.contains (o2))
            return -1;
        else if (preferredUnits.contains (o2) && !preferredUnits.contains (o1))
            return 1;
    }

    return ((Unit) o1).compareTo(o2);
}

}
