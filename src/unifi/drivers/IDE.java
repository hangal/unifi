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

package unifi.drivers;

import java.io.IOException;

import unifi.units.FieldUnit;

/** interface to IDEs for semantic coloring */
public class IDE {

	/** these fields return an equiv class # for the respective input.
	 * if the equiv class # is not available or not found, returns -1.
	 */
	public static int getEquivClassNumForField(String fieldname)
	{
		FieldUnit fu = FieldUnit.lookup(fieldname);
		if (fu == null)
			return -1;				
		return fu.clusterNum;
	}

	/** pos starts from 0 */
	public static int getEquivClassNumForMethodParam(String methodSig, int pos)
	{
		return -1;
	}

	public static int getEquivClassNumForMethodRV(String methodSig)
	{
		return -1;		
	}

	/** for an integer type const with value 2, pass in new Integer(2).
	 * etc.
	 */
	public static int getEquivClassNumForConst(String methodSig, String lineNum, Object val)
	{
		return -1;
	}
	
	public static int getEquivClassNumForLocalVar(String methodSig, String varName, String lineNum)
	{
		return -1;		
	}
	
	/** filename is the full path to the units file */
	public static void loadUnits (String filename) throws IOException
	{
		
	}
}
