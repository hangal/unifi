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

package unifi.drivers;

import java.io.*;

import unifi.UnitCollection;

/** print path from the first unit to the second. 
 * both units are specified in terms of their index in the unit collection */
public class Query {

private static void print_usage_and_die()
{
    System.out.println ("Usage: query <units file> <unit#1> <unit#2>");
    System.exit(2);
}

public static void main (String args[]) throws Exception
{
    if (args.length != 3)
        print_usage_and_die();

    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[0]));
    UnitCollection uc = (UnitCollection) ois.readObject();
    ois.close();

    uc.print_path(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
}

}
