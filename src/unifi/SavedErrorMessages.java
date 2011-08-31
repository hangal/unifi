package unifi;

import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.Set;

/** simple class to gather all the messages about missing classes and print them together at the end */
public class SavedErrorMessages {

	static Set<String> messages = new LinkedHashSet<String>(); // Set so that we avoid duplication
	public static void add (String s) { messages.add(s); }
	public static void print (PrintStream w)
	{
		if (messages.size() == 0)
			return;

		w.println ("Important Error messages during the run");
		for (String s: messages)
		{
			w.println (s);
		}
	}
}
