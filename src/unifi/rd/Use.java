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

package unifi.rd;

import java.util.*;

import unifi.util.Util;

class Use
{

    private int _index;
    private int _position;
    private boolean _is_double, _is_iinc;
    private int _id;
    private Set _def_set;

    private static int _next_id = 0;

    public Use (int index, int pos, boolean is_double, boolean is_iinc)
    {
        Util.ASSERT (! (is_iinc && is_double));

        _index = index;
        _position = pos;
        _is_double = is_double;
        _is_iinc = is_iinc;
        _id = _next_id;
        _next_id++;
    }

    public void set_def_set (Set s)
    {
        _def_set = s;
    }

    public Set get_def_set ()
    {
        return _def_set;
    }

    public int get_index ()
    {
        return _index;
    }

    public int get_position ()
    {
        return _position;
    }

    public boolean is_double ()
    {
        return _is_double;
    }

    public boolean is_iinc ()
    {
        return _is_iinc;
    }

    public boolean equals (Object o)
    {
        if (! (o instanceof Use))
        {
            return false;
        }

        Use d = (Use) o;
        return ( (_index == d._index) && (_position == d._position));
    }

    public int hashCode () { return (_index  * 100) + _position; } 

    public String toString ()
    {
        StringBuffer sb = new StringBuffer ("Use " + _id + "(LV " + _index +
                                            ", pos " + _position + ")");
        if (_is_double)
        {
            sb.append (" (Double)");
        }
        if (_is_iinc)
        {
            sb.append (" (IINC)");

        }
        return sb.toString ();
    }
}
