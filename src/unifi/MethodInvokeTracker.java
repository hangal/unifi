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

package unifi;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** maintains a map of which method calls which other method, currently used only for printing * 
 */
public class MethodInvokeTracker {

	// lets just maintain both maps for now, they have the same information *
private Map<MethodUnits, Set<MethodUnits>> calleeToCallerMap = new LinkedHashMap<MethodUnits, Set<MethodUnits>>();

/** adds a dependency from caller to callee */
public void addMethodDep(MethodUnits caller, MethodUnits callee)
{
	Set<MethodUnits> s = calleeToCallerMap.get(callee);
	if (s == null)
	{
		s = new LinkedHashSet<MethodUnits>();
		calleeToCallerMap.put (callee, s);
	}
	s.add(caller);
}

public void printMethodDeps(PrintStream out)
{
	int i = 1;
	for (Map.Entry<MethodUnits, Set<MethodUnits>> me : calleeToCallerMap.entrySet())
	{
		out.println (i++ + ". Callee: " + me.getKey().full_sig());
		int j = 0;
		for (MethodUnits mu : me.getValue())
			out.println ("\t" + j + ". " + mu.full_sig());
	}
}
}
