package unifi;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import unifi.units.Unit;
import unifi.util.Util;

public class WatchList {

public static final Logger logger = Logger.getLogger("unifi");
private static List<String> unitsToWatchStr = new ArrayList<String>();
private static List<Unit> unitsToWatch = new ArrayList<Unit>();

/** list of units that must be kept separate
// the unit's toString() must start with the given watch string
 * @param filename the filename to read units from, one on a line, lines starting with # are ignored
 * @throws IOException
 */
public static void setup(String filename) throws IOException
{
	if (filename != null)
	{
	    LineNumberReader l = new LineNumberReader(new FileReader(filename));
	    String s;
	    while ((s = l.readLine()) != null)
	    {
	    	s = s.trim();
	    	if ("".equals(s) || s.startsWith("#"))
	    		continue;
	    	unitsToWatchStr.add(s);
	    	unitsToWatch.add(null);
	    }
	}
}

public static void markNewUnit(Unit u)
{
    for (int i = 0; i < WatchList.unitsToWatchStr.size(); i++)
    	if (u.toString().startsWith(WatchList.unitsToWatchStr.get(i)))
    	{
    		Util.ASSERT (unitsToWatch.get(i) == null);
	    	unitsToWatch.set(i, u);
	    	u.watchColor = i;
	    	break;
    	}
}

public static void checkUnify (Unit a, Unit b, BCP bcp)
{
	Unit foundRep = null;
	Unit foundU = null;
	for (Unit u: unitsToWatch)
	{
		if (u == null)
			continue;
		Unit rep = (Unit) u.find();
		if (foundRep == null)
		{
			foundRep = rep;
			foundU = u;
		}
		else
			if (rep == foundRep)
			{
		    	logger.severe ("The twain did meet! Merge of " + a + " and " + b + ". at bcp " + bcp + " with stack trace:\n" + Util.stackTrace()
		    			+ "\n Location: " + bcp);
				Util.breakpoint (" units to watch: merged: " + rep + " AND " + foundRep);
				Unit._current_unit_collection.compute_reps();

				List<Unit> list1 = Unit._current_unit_collection.get_reps().get(foundRep);
				System.out.println ("\nrep1 is " + foundRep + "\nrepresents " + list1.size() + " units:");
				for (Unit u1: list1)
					System.out.println (u1);

				List<Unit> list2 = Unit._current_unit_collection.get_reps().get(rep);
				System.out.println ("\nrep2 is " + rep + "\nrepresents " + list2.size() + " units:");
				for (Unit u1: list2)
					System.out.println (u1);

				Unit._current_unit_collection.print_path (u, foundU);
			}
	}

	/*
	if (a.watchColor == -1 && b.watchColor == -1)
		return true;
	if (a.watchColor == b.watchColor)
		return true;

	// ids not equal
	if (a.watchColor != -1 && b.watchColor != -1)
		return false;

	// flip a and b if a is the one that doesn't have an id
	if (a.watchColor == -1 && b.watchColor != -1)
	{
		Unit x = a; a = b; b = x;
	}

	// now a's id has to go to b and all its friends
	// could be an expensive op since it has to iterate over all units
	for (Unit u: Unit._current_unit_collection.get_units())
	{
		if (u.find() == b.find())
		{
			if (u.watchColor == -1)
				System.out.println ("Watch color " + a.watchColor + " spreads to: " + u);
			else if (u.watchColor != a.watchColor)
			{
				System.err.println ("Warning: setting color from " + u.watchColor + " to " + a.watchColor + " for unit: " + u);
				return false;
			}
			u.watchColor = a.watchColor;
		}
	}
	*/
}

}
