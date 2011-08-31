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

import java.io.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

import unifi.util.Util;

/** represents a bytecode location in the analyzed program */
public class BCP implements Serializable {

private int _pos, _src_line; // src_line = 0 means it is unavailable
private String _class_name;
private String _method_name_and_sig;
private boolean _is_meet;

public BCP (MethodGen mg, ConstantPoolGen cpgen, int pos)
{
    _pos = pos;
    LineNumberTable lnt = mg.getLineNumberTable(cpgen);
    _src_line = (lnt != null) ? lnt.getSourceLine(pos) : 0;
    _class_name = mg.getClassName().intern();
    _method_name_and_sig = (mg.getName() + mg.getSignature()).intern();
}

public BCP (MethodGen mg, ConstantPoolGen cpgen, int pos, boolean is_meet)
{
    this (mg, cpgen, pos);
    Util.ASSERT (is_meet == true);
    _is_meet = true;
}

public String get_class_name () { return _class_name; }
public int get_src_line () { return _src_line; }
public int get_pos() { return _pos; }
public String get_method_name_and_sig() { return _method_name_and_sig; }

public String toString ()
{
    if (_is_meet)
    	return "Meet operator in class " + _class_name + "." + _method_name_and_sig +", line " + _src_line + ", pos " + _pos;
    else
    {
    	String line_str = (_src_line > 0) ? Integer.toString(_src_line) : "?";
        return "class " + Util.shorten_package_for_class_name(_class_name) + "." + _method_name_and_sig + " line " + line_str + ", pos " + _pos;
    }
}

public int hashCode ()
{
    return _class_name.hashCode() ^ _method_name_and_sig.hashCode() ^ _pos ^ _src_line ^ ((_is_meet) ? 1: 0);
}

public boolean equals (Object o)
{
    if (!(o instanceof BCP))
	return false;

    BCP other = (BCP) o;

    return (_class_name.equals (other._class_name) &&
        (_method_name_and_sig.equals(other._method_name_and_sig)) &&
	   (_src_line == other._src_line) &&
	   (_pos == other._pos) &&
	   (_is_meet == other._is_meet));
}

}


