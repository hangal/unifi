package seaview;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import unifi.util.Pair;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Runtime {

	final static boolean PRINT = false;
	static {
		try {
			IPInfo.load("SEAVIEW" + File.separatorChar + "ips");
			java.lang.Runtime.getRuntime().addShutdownHook(new ShutdownThread());
		} catch (Exception e) {
			System.err.println("Unable to load Seaview info file");
			System.exit(2);
		}
	}
	
	static List<Pair<Double,Integer>> logs = new ArrayList<Pair<Double,Integer>>();

	public static StringBuilder log (StringBuilder sb, int value, int IPnum)
	{
		if (PRINT)
			System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		sb.append (":::SV" + IPnum + ":::" + value + ":::");
		logs.add(new Pair<Double, Integer>((double) value, IPnum));
		return sb;
	}

	public static StringBuilder log (StringBuilder sb, long value, int IPnum)
	{
		if (PRINT)
			System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		sb.append (":::SV" + IPnum + ":::" + value + ":::");
		logs.add(new Pair<Double, Integer>((double) value, IPnum));
		return sb;
	}

	public static StringBuilder log (StringBuilder sb, boolean value, int IPnum)
	{
		if (PRINT)
			System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		sb.append (":::SV" + IPnum + ":::" + value + ":::");
		logs.add(new Pair<Double, Integer>(value?1.0:0.0, IPnum));
		return sb;
	}
	public static StringBuilder log (StringBuilder sb, float value, int IPnum)
	{
		if (PRINT)
			System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		sb.append (":::SV" + IPnum + ":::" + value + ":::");
		logs.add(new Pair<Double, Integer>((double) value, IPnum));
		return sb;
	}

	public static StringBuilder log (StringBuilder sb, double value, int IPnum)
	{
		if (PRINT)
			System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		sb.append (":::SV" + IPnum + ":::" + value + ":::");
		logs.add(new Pair<Double, Integer>((double) value, IPnum));
		return sb;
	}

	public static StringBuilder log (StringBuilder sb, Object value, int IPnum)
	{
		if (PRINT)
			System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		sb.append (":::SV" + IPnum + ":::" + value + ":::");
//		logs.add(new Pair<Double, Integer>(0.0, IPnum));
		return sb;
	}

	public static void log (int value, int IPnum)
	{
		System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		logs.add(new Pair<Double, Integer>((double) value, IPnum));
	}

	public static void log (long value, int IPnum)
	{
		System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		logs.add(new Pair<Double, Integer>((double) value, IPnum));
	}

	public static void log (float value, int IPnum)
	{
		System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		logs.add(new Pair<Double, Integer>((double) value, IPnum));
	}

	public static void log (double value, int IPnum)
	{
		System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
		logs.add(new Pair<Double, Integer>((double) value, IPnum));
	}

	public static void log (Object value, int IPnum) // usually a string
	{
		System.out.println ("Seaview runtime received v=" + value + " from ipnum " + IPnum + " details = " + IPInfo.lookup(IPnum));
	//	logs.add(new Pair<Object, Integer>(value, IPnum));
	}
	
	public static String getLogs()
	{
		List<Pair<Double, Integer>> numericLogs = new ArrayList<Pair<Double, Integer>>();
		for (Pair<Double, Integer> p: logs)
			if (p.getLeft() instanceof Double)
				numericLogs.add(p);
		
		Type listType = new TypeToken<List<Pair<Double, Integer>>>() {}.getType();
		return new Gson().toJson(logs, listType);
	}
	
	public static void saveLogs(String filename) throws UnsupportedEncodingException, FileNotFoundException
	{
		PrintWriter pw = new PrintWriter (new OutputStreamWriter(new FileOutputStream(filename), "UTF-8"));

		pw.println ("var logs = " + getLogs() + ";");
		pw.close();
	}
	
	public static class ShutdownThread extends Thread {
		@Override
		public void run()
		{
			try {
				saveLogs("SEAVIEW" + File.separatorChar + "run.logs.js");
			} catch (Exception e) {
				System.err.println ("Exception saving run logs: " + e);
			}
		}
	}	
	public static void main (String args[])
	{
		List<Pair<Object, Integer>> list = new ArrayList<Pair<Object, Integer>>();
		list.add(new Pair<Object,Integer>(new Object(), 10));
		Type listType = new TypeToken<List<Pair<Object, Integer>>>() {}.getType();
		new Gson().toJson(list);
		System.out.println ("done");
	}
}
