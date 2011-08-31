package seaview;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.RowSorterEvent.Type;

import org.apache.bcel.generic.ReferenceType;

import unifi.units.Unit;

import com.google.gson.Gson;

/** manages info about instrumentation points */
public class IPInfo implements Serializable
{
	private static final long serialVersionUID = 1L;

	public int IPNum;
	public int dimensionID;
	public int callSiteNum;
	public String valueSig;
	public String className;
	public String methodName;
	public String methodSig;	
	public int unit_num;
	public int lineNum;
	public String type;
	public boolean is_enum;
	
	static int global_IP_num;
	static List<IPInfo> IP_infos = new ArrayList<IPInfo>();

	public static synchronized int store_IP_info (Unit u, int callSiteNum, String valueSig, String className, String methodName, String methodSig, int lineNum)
	{
		IPInfo info = new IPInfo();
		info.dimensionID = u.seaview_rep_id;
		info.unit_num = u.seaview_id;
		info.IPNum = global_IP_num;
		info.callSiteNum = callSiteNum;
		info.valueSig = valueSig;
		info.className = className;
		info.methodName = methodName;
		info.methodSig = methodSig;
		info.lineNum = lineNum;
		org.apache.bcel.generic.Type t = u.getType();
		if (t == null)
			info.type = "null";
		else
		{
			info.type = t.toString();
			if (t instanceof ReferenceType)
			{
				ReferenceType rt = (ReferenceType) t;
				try {
					if (rt.isAssignmentCompatibleWith(org.apache.bcel.generic.Type.getType(java.lang.Enum.class)))
					{
						info.is_enum = true;
					}
				} catch (ClassNotFoundException cnfe)  { System.err.println ("ERROR: " + cnfe); }
			}
		}
		
		IP_infos.add(info);
		return global_IP_num++;
	}
	
	public static IPInfo lookup(int num)
	{
		if (num < IP_infos.size())
			return IP_infos.get(num);
		else
			return null;
	}
	
	public static void commit(String filename) throws FileNotFoundException, IOException
	{
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
		oos.writeObject(IP_infos);
		oos.close();

		PrintWriter pw = new PrintWriter (new OutputStreamWriter(new FileOutputStream(filename + ".js"), "UTF-8"));
		pw.println ("var ips = " + new Gson().toJson(IP_infos) + ";");
		System.out.println (IP_infos.size() + " code locations related to logging were instrumented");
		pw.close();
	}

	public static void load(String filename) throws FileNotFoundException, IOException, ClassNotFoundException
	{
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename));
		IP_infos = (List<IPInfo>) ois.readObject();
		ois.close();
	}
	
	public String toString()
	{
		return ("Method: " + className + "." + methodName + methodSig + " callsite: " + callSiteNum + " unit cluster:" + dimensionID + " value sig: " + valueSig);
	}

}
