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

/*
UniFi: A tool for detecting and root-causing bugs in Java programs.
Copyright (C) 2002 Sudheendra Hangal

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
    
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

For more details, please see: 
http://www.gnu.org/copyleft/gpl.html

*/
    
package unifi.drivers;

import java.io.File;
import java.util.*;
import unifi.gui.*;

public class Show extends java.lang.Thread
{

private static String[] _source_paths;

public Show (ThreadGroup tg)
{
    super (tg, "GUI-thread");
}

public static void parse_source_path ()
{
    String path = System.getProperty ("unifi.sp");
    if (path == null)
    {
        _source_paths = new String[1];
        _source_paths[0] = ".";
        return;
    }

    StringTokenizer st = new StringTokenizer (path, File.pathSeparator);
    _source_paths = new String[st.countTokens()];
    for (int i = 0 ; i < _source_paths.length ; i++)
        _source_paths[i] = st.nextToken();
}

public static void main(String[] args)
{
    parse_source_path ();
    gui g = new gui (_source_paths, args[0]);
    g.pack ();
    g.setVisible (true);
}

public void run ()
{
    String a[] = new String[1];
    a[0] = "Live Run";
    main(a);
}

}
