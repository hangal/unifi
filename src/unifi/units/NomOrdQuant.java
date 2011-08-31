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

/* various attributes that helps track the kind of
 * the unit - e.g. whether is (nominal, ordinal or quantitative) */
public class NomOrdQuant implements Serializable {

private boolean isQuantOrOrd, isBitwise, isEqualsCompared, dynamicTypeChecked; // false by default

public void setQuantOrOrd() 
{
	isQuantOrOrd = true; 
}

public void setBitOpPerformed() 
{ 
	isBitwise = true; 
}

public void setEqualityChecked() 
{ 
	isEqualsCompared = true; 
}

public void setDynamicTypeChecked() 
{ 
	dynamicTypeChecked = true; 
}

public boolean quantOrOrd() 
{ 
	return isQuantOrOrd; 
}

public boolean is_bit_encoded() 
{ 
	return isBitwise; 
}

public boolean isEqualityChecked() 
{ 
	return isEqualsCompared; 
}

public boolean isDynamicTypeChecked() 
{ 
	return dynamicTypeChecked; 
}

/** merges 2 attribute objects, setting both to the 
 * meet of the two */
public void merge (NomOrdQuant other) 
{
    isQuantOrOrd |= other.isQuantOrOrd;
    other.isQuantOrOrd = isQuantOrOrd;
    isBitwise |= other.isBitwise;
    other.isBitwise = isBitwise;
    isEqualsCompared |= other.isEqualsCompared;
    other.isEqualsCompared = isEqualsCompared;
    dynamicTypeChecked |= other.dynamicTypeChecked;
    other.dynamicTypeChecked = dynamicTypeChecked;
}

}
