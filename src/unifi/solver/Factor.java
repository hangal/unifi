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

package unifi.solver;
import unifi.*;

class Factor {

public static void print (String s) { System.out.println (s); }

// returns true if n is a prime
// returns false if n is not a prime
public static boolean is_a_prime (int n)
{
    for (int i = 2; i < n; i++)
    {
        if (n%i == 0)
            return false;
    }

    return true;
}

// given a number x, print out all it's factors
public static void print_out_factors_of (int x)
{
    for (int n = 2; n < x; n++)
    {
        if (x%n == 0) 
        { 
      
                print (n + " is a  factor of " + x); 
        }
    }
}

public static void main (String args[])
{
    print_out_factors_of (6);
    print_out_factors_of (165);
    print_out_factors_of (10220);
    print_out_factors_of (4);
    print_out_factors_of (14365);
    print_out_factors_of (2743100);
    print_out_factors_of (2790763);
    print_out_factors_of (76568);
    print_out_factors_of (97);
    print_out_factors_of (78);
    print_out_factors_of (6);
    print_out_factors_of (12);


    
}


}

