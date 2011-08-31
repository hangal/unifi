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

package unifi.contextsensitive;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.logging.Logger;

import unifi.MethodUnits;
import unifi.oo.MethodResolver;
import unifi.util.Util;

/** driver class for the context sensitive portion of the analysis.
 * maintains a worklist and propagates updates of method summaries.
 */
public class ContextSensitiveAnalysis {
private static int nTotalUpdates, nWorklistUpdates, nRequeues, nMethodUnits;
public static LinkedHashSet<MethodUnits> worklist = new LinkedHashSet<MethodUnits>(); // this is a global, could be in any class

private static Logger logger = Logger.getLogger("unifi.Unit");

private static void addAllMethodUnitsToWorklist()
{
	nTotalUpdates = 0;
	Collection<MethodUnits> allMethodUnits = MethodResolver.get_all_method_units();
	logger.info("Starting worklist with " + allMethodUnits.size() + " methods");

	for (MethodUnits mu : allMethodUnits)
        if (mu != null && !mu.isGolden())
        	addToWorklist(mu);
	nMethodUnits = allMethodUnits.size();
	nWorklistUpdates = 0;
}

public static void addToWorklist(MethodUnits mu)
{
    Util.ASSERT (mu != null);
	nTotalUpdates++;
	nWorklistUpdates++;
	if (worklist.contains(mu))
	{
		worklist.remove(mu);
		System.out.println ("Worklist: requeuing method : " + mu);
		nRequeues++;
	}
	worklist.add(mu);
	System.out.println ("Worklist: queued method " + nWorklistUpdates + " " + mu);
}

public static void doIt()
{
	addAllMethodUnitsToWorklist();
	while (true)
	{
		Iterator<MethodUnits> it = worklist.iterator();
		if (!it.hasNext())
			break;
		MethodUnits mu = it.next();
		it.remove();
		System.out.println ("Worklist: dequeued method : " + mu);
		if (mu.isGolden())
		{
			System.out.println ("Dropping golden method units: " + mu);
		}
		mu.getMethodSummary().updateClones();
	}
}

public static void printStats(PrintStream out)
{
	out.println ("Total method units: " + nMethodUnits);
	out.println ("Total method updates: " + nTotalUpdates);
	out.println ("Total worklist updates: " + nWorklistUpdates);
	out.println ("Total worklist requeues: " + nRequeues);

}
}
