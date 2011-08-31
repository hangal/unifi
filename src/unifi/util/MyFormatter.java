package unifi.util;

import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import unifi.SavedErrorMessages;

public class MyFormatter extends Formatter {
    @Override
    public String format(final LogRecord r) {
    	String s = r.getLoggerName() + " " + r.getLevel() + ": " + r.getMessage() + "\n";
    	if (r.getLevel() == Level.SEVERE)
    		SavedErrorMessages.add(s);
    	return s;
    }
}
