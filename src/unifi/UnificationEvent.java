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
import java.util.*;

import unifi.units.Unit;
import unifi.util.Util;


/* a point in the code that unifies 2 units */
public class UnificationEvent implements Serializable, Comparable {

private Unit _a, _b; // the 2 units being unified
private BCP _bcp;    // the position at which they were unified
private UnificationEvent _depends_on; // any other unif event this one depends on
int id;

public UnificationEvent (Unit a, Unit b, BCP bcp)
{
    _a = a;
    _b = b;
    _bcp = bcp;
}

public void setId(int id)
{
	this.id = id;
}

public int getId() { return this.id; }

public UnificationEvent (Unit a, Unit b, BCP bcp, UnificationEvent e)
{
    _a = a;
    _b = b;
    _bcp = bcp;
    _depends_on = e;
}

public UnificationEvent depends_on() { return _depends_on; }

// given a collection of units uc, returns a collection
// of unification events from among events collection ec
// which is associated with any of those units
public static Collection<UnificationEvent> select_events(Collection<Unit> uc)
{
    Set<UnificationEvent> c = new LinkedHashSet<UnificationEvent>();

    for (Iterator it = uc.iterator(); it.hasNext(); )
    {
        Unit u = (Unit) it.next();
        Collection<UnificationEvent> units_events = u.getUnificationEvents();
        if (units_events != null)
            c.addAll (units_events);
    }

    return c;
}

public BCP get_bcp() { return _bcp; }
public Unit get_unit_a() { return _a; }
public Unit get_unit_b() { return _b; }

// note: natural ordering inconsistent with equals
// helps print the more important unification events first
public int compareTo(Object o)
{
    Util.ASSERT (o instanceof UnificationEvent);
    UnificationEvent other = (UnificationEvent) o;

    // first differentiate based on primitives
    if (_a.isPrimitiveType() && !other._a.isPrimitiveType())
        return -1;
    if (!_a.isPrimitiveType() && other._a.isPrimitiveType())
        return 1;

    // next differentiate based on strings
    if (_a.isStringType() && !other._a.isStringType())
        return -1;
    if (!_a.isStringType() && other._a.isStringType())
        return 1;

    // next differentiate based on whether it is a field, higher score means lower in the order
    int this_score = (_a.isField() ? 1 : 0) + (_b.isField() ? 1 : 0);
    int other_score = (other._a.isField() ? 1 : 0) + (other._b.isField() ? 1 : 0);

    return other_score - this_score;
}

public String toString()
{
    return "Unification at " + _bcp + " between:" + "\n  " + _a + "\n  " +_b;
    // + "\na.rep = " + _a.find() + "\nb.rep=" + _b.find();
}

}

