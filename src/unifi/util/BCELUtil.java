package unifi.util;

import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;

public class BCELUtil {
    public static Type simplifiedType(Type t)
    {
		int this_state = -1;
		if (t == Type.BOOLEAN || t == Type.BYTE || t == Type.SHORT ||  t == Type.CHAR || t == Type.INT)
			return Type.INT;
		else if (t == Type.LONG || t == Type.FLOAT || t == Type.DOUBLE)
			return t;
		else
			return Type.OBJECT; // object
    }

    public static boolean isStringType(Type type)
    {
	    if (!(type instanceof ObjectType))
	        return false;
	//    String cname = ((ObjectType) type).getClassName();
	//    return type.getSignature().equals("Ljava.lang.String;");
	    String cname = ((ObjectType) type).getClassName();
	    return cname.equals ("java.lang.String");
    }
}
