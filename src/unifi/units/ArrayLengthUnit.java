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


import java.io.*;
import org.apache.bcel.generic.*;

import unifi.util.Util;
/**
 * <p>Title: </p>
 * <p>Description: Array Length Unit</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ArrayLengthUnit extends Unit implements Serializable
{
public ArrayLengthUnit(Unit a)
{
    super (BasicType.INT);
    this.lengthOf = a;
    Util.ASSERT (a.lengthUnit == null);
    a.lengthUnit = this;
}

public String toString ()
{
    StringBuffer sb = new StringBuffer ( "Length of " + lengthOf + " (cluster #" + lengthOf.clusterNum + ")");
    appendAttrString (sb);
    return sb.toString();
}

public int hashCode ()
{
    return lengthOf.hashCode() + 1;
}

public boolean equals (Object o)
{
    if (!(o instanceof ArrayLengthUnit))
	return false;

    ArrayLengthUnit other = (ArrayLengthUnit) o;

    boolean equal = (lengthOf.equals(other.lengthOf));
    return equal;
}

}
