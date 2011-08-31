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

package unifi.units;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.Type;

import unifi.BCP;
import unifi.UnificationEvent;
import unifi.UnionFindObject;
import unifi.UnitCollection;
import unifi.WatchList;
import unifi.util.BCELUtil;
import unifi.util.Util;

/* This class represents the elments which can represent a unit.
 This is a class which has to be subclassed further by specific
 particular kinds of units */

public class Unit extends UnionFindObject implements Serializable, Comparable, Cloneable
{
// need to implement Cloneable for method summaries
public static final long serialVersionUID = 1L;

private static Logger logger = Logger.getLogger("unifi.Unit");
private static Logger unify_logger = Logger.getLogger("unifi.Unit");

private static final boolean all_unif_events = (!"no".equals(System.getProperty ("unifi.all_unif_events")));
public static UnitCollection _current_unit_collection = new UnitCollection();
private static boolean unificationInProgress;

public int clusterNum = -1;
public int seaview_id = -1, seaview_rep_id = -1;
public boolean connectedToSingleUnit = false;
// public Set<String> displayNames; // a name for the class this unit belongs to, that can be displayed to the user

public List<UnificationEvent> unifEvents; // unif events associated with this unit.
// a collection of all units
protected boolean dimensionLess;
public NomOrdQuant unitAttribs;
private int id; // unique id for every unit

public Unit elementOf; //points to array type of this element
public Unit arrayOf; //points to member types, if this is array type
public Unit lengthUnit; //points to array length unit, if this is array type
public Unit lengthOf; //points to array for which this unit tracks the length

// important: do not use type in any equals or hashcode computation because it may be forced later!
protected Type type; //The Java type of this unit
protected boolean isTypeForced = false; // was the type forced (i.e. unclear when the unit was created, and had to be set later) ?
private int goldenId = -1; // is a golden unit
private boolean isGolden;
public int watchColor = -1; // this is for debugging only, protected because WatchList needs to access it

//protected Type isUnifiedWithField; // tracks whether this unit is unified w a field.

/* not maintaining display names in unit any more, look in unit collection.
public String getDisplayName() {
	if (displayNames == null)
		return null;

	for (String s : displayNames)
		return s;

	return null;
}

public void addDisplayName(String name) {
	this.displayNames.add(name);
}

public void setDisplayNames(Set<String> names) {
	this.displayNames = names;
}
*/

public void setId(int id) { this.id = id; }
public int getId() { return this.id; }
public boolean isDimensionLess() { return dimensionLess; }
public void set_is_dimension_less(boolean b) { dimensionLess = b; }
public NomOrdQuant unitAttribs () { return unitAttribs; }

public void setGoldenId(int id) { goldenId = id; }
public int getGoldenId() { return goldenId; }
public boolean isGolden() { return isGolden; }
public void markGolden() { isGolden = true; }

public Unit (Type t)
{
    super ();
    type = t;
    elementOf = null;
    arrayOf = null;
    unitAttribs = new NomOrdQuant();
}

/** publicizing Unit's clone since other classes need to call Unit.clone().
 * (Object's clone() is protected) */
public Object clone()
{
	try { return super.clone(); }
	catch (CloneNotSupportedException cne) { Util.die(); }
	return null; // silly requirement to return.
}

/** really used only to see how much memory is being used by unif events */
public void clearEvents()
{
	if (unifEvents != null)
		unifEvents.clear();
}

public void reset()
{
    super.reset(); // resets the union find data structure
    dimensionLess = false;
    unitAttribs = new NomOrdQuant();
    unifEvents = null;
}

public void setElementOf (Unit u) { elementOf = u; }
public void setArrayOf (Unit u) { arrayOf = u; }
public Type getType () { return type; }

public void forceType(Type t) {
	type = t;
	isTypeForced = true;
}

public Unit getElementOf () { return elementOf; }

/** like getElementOf, but follows it to all the way to the basic non-element type */
public Unit getElementOfBase () {
	Unit x = this;

	while (x != null && (x instanceof ElementUnit))
		x = ((ElementUnit) x).getElementOf();
	return x;
}


public boolean hasArrayOf() { return (arrayOf != null); }
/** allocate array of lazily on demand. will never return null */
public Unit getArrayOf ()
{
    Util.ASSERT (!(type instanceof BasicType));

    // array_of may be allocated lazily; if already allocated, just return what we have
    if (arrayOf != null)
        return arrayOf;

    // if _type is null or not an array type (probably j.l.O),
    // just set the element's type to j.l.O (enhancements: assert that _type is really j.l.O)
    // add the BCP where this get_array_of was allocated for future tracking.
    Type t = null;
    if ((type != null) && (type instanceof ArrayType))
        t = ((ArrayType) type).getElementType ();
    else
    {
//
//         if (type != null)
//            t = Type.getType ("Ljava/lang/Object;"); // this may not be right - what if it's a int array
//
    }

    arrayOf = new ElementUnit (t);
    arrayOf.setElementOf (this);
    registerUnit(arrayOf);

    return arrayOf;
}

/* allocate length unit lazily, on demand. will never return null */
public Unit getLengthUnit ()
{
    Util.ASSERT (!(type instanceof BasicType));
    if (lengthUnit == null)
    {
        lengthUnit = new ArrayLengthUnit (this);
        registerUnit(lengthUnit);
        lengthUnit.lengthOf = this;
    }

    return lengthUnit;
}

public boolean hasLengthUnit() { return (lengthUnit != null); }

public void setLengthUnit(ArrayLengthUnit u)
{
	this.lengthUnit = u;
	u.lengthOf = this;
}

/** registers a new unit in the UnitCollection. remember to call this every time a unit object is created */
public static void registerUnit (Unit u)
{
    _current_unit_collection.add (u);
    // check that the collection contains it immediately after adding.
    // this check sometimes finds bugs with bad hashcode/equals functions in units.
    Util.ASSERT (_current_unit_collection.contains(u));

    int size = _current_unit_collection.size();
    // print every so often how many unit objs we've created
    if (size % 10000 == 0)
        logger.info ("# units = " + size);

    WatchList.markNewUnit(u);
}

// check for type compatibility between this and that
// NOTE: this method generates warnings: most of them can be ignored currently though.
private void checkTypes(Unit that)
{
    boolean report_mismatch = true;

    // not sure if the following exceptions for j.l.Object are correct, can be removed.
    if (this.type.toString().equals ("java.lang.Object"))
        report_mismatch = false;
    else if (that.type.toString().equals ("java.lang.Object"))
        report_mismatch = false;
    else if (((this.type == Type.INT) || (this.type == Type.CHAR) || (this.type == Type.LONG) ||
              (this.type == Type.SHORT) || (this.type == Type.BYTE))
             &&
             ((that.type == Type.INT) || (that.type == Type.CHAR) || (this.type == Type.LONG) ||
              (that.type == Type.SHORT) || (that.type == Type.BYTE)))
    {
        // allow free assignment between int/char/byte/short/long
        report_mismatch = false;
    }

	// if either this or that is a local variable unit with uncertain type/name,
	// ignore type checking - this generated false type mismatches, e.g. on apache-ant-1.7.1/lib/ant.jar
	// in method org.apache.tools.ant.taskdefs.Checksum.validateAndExecute()

	boolean uncertain = ((this instanceof LocalVarUnit) && !((LocalVarUnit) this).isCertain());
    uncertain |= ((that instanceof LocalVarUnit) && !((LocalVarUnit) that).isCertain());


    if (report_mismatch)
    {
        // make sure type is basic or reference type. bcel defines other kinds of types
        // like return address type which i think we will never see.
        Util.ASSERT ((this.type instanceof BasicType) || (this.type instanceof ReferenceType));
        Util.ASSERT ((that.type instanceof BasicType) || (that.type instanceof ReferenceType));
        if ((this.type instanceof BasicType) != (that.type instanceof BasicType))
        {
            logger.warning ("SEVERE WARNING (" + (uncertain ? "uncertain":"certain") + "): Basic vs. ref type mismatch:");
            logger.warning(this.type + " v/s " + that.type + ". Units are\n  " + this + " and\n  " + that);
            return;
        }

        if (this.type instanceof BasicType)
        {
        	String thisTypeStr = this.type.toString();
        	String thatTypeStr = that.type.toString();
            if (!thisTypeStr.equals(thatTypeStr))
            {
                if (!(this instanceof CheckcastUnit) && !(that instanceof CheckcastUnit))
                {
	                logger.warning ("Type mismatch in primitive type unification (" + (uncertain ? "uncertain":"certain") + "): " + this.type + " v/s " + that.type
	                                + "\nUnits are \n  " + this + " and\n  " + that);

	                boolean thisIsDouble = thisTypeStr.equals("long") || thisTypeStr.equals("double");
	                boolean thatIsDouble = thatTypeStr.equals("long") || thatTypeStr.equals("double");
	                if (thatIsDouble != thisIsDouble)
	                	logger.warning ("Above could be a really serious error - 1 word vs. 2");
                }
            }
        }
        else
        {
        	// if ref type, check if the type can be cast (in either direction)
            // Q: is it necessary that the RHS can be cast to LHS only ?
            // in that case, we may be able to make this just other._type.isCastableTo(this._type)

            // unfortunately, the following check can generate false warnings (as it did on bddbdbb)
            // consider the code:
            // interface I { void m(); } class A { void m() { } } class B extends A implements I { void m() { } }
            // then B.m() uses the method param units of A.m(), but A's this param is not compatible with I
            // (B implements I, but A doesn't)
            if (!(this instanceof CheckcastUnit) && !(that instanceof CheckcastUnit))
            {

	            ReferenceType r_this = (ReferenceType) this.type;
	            ReferenceType r_that = (ReferenceType) that.type;
	            try {

                // Q: should we use isAssignmentCompatibleWith() or isCastableTo() ?
                // haven't looked at the difference carefully - sgh.
	                if (!r_this.isCastableTo (r_that) && !r_that.isCastableTo(r_this))
	                {
	                	String msg = "Type mismatch in reference type unification (" + (uncertain ? "uncertain":"certain") + "): " + r_this + " vs. " + r_that
                        			+ "\nUnits are \n  " + this + " and\n  " + that;

	                    if (uncertain)
	                    	logger.warning(msg);
	                    else
	                    	logger.warning(msg); // logger.severe(msg); -- disabling because MethodSummary.updateClone() calls unify but doesn't have the hack like unifi_state.transfer's InvokeInstruction to waive type checking when its ok
	                }
	            } catch (ClassNotFoundException cnfe) {
	                logger.severe ("Unable to find class on classpath when checking cast-ability between types: " + r_this + " and " + r_that);
	                	//	                                    + "\nUnits are " + this + " and " + that);
	            }
            }
        }
    }
}

/* unifies this with that.
   also merges this.length with that.length (and all it's chains)
   merges the array_of and the element_of chain depending on the given boolean flag.
   in practice we merge only downstream, i.e. the array_of chain.
   note: merging 2 length units does not unify the corresponding array's.
 */

private void realUnify (Unit that, boolean unify_array_of_chain,
                         boolean unify_element_of_chain, BCP bcp, UnificationEvent depends_on, boolean markAsConnectedToSingleUnit, boolean waiveTypeChecking)
{
    if (unify_logger.isLoggable(Level.FINER)) { logger.finer ("unifying: \n  " + this + "\n  " + that); }

    Util.ASSERT (Unit._current_unit_collection.get_units().contains(this));
    Util.ASSERT (Unit._current_unit_collection.get_units().contains(that));

    // these checks effectively check if one is a length unit (an int)
    // and the other is an array (a Reference type)
    // a failure of these asserts really means the types are screwed up
    if ((this.lengthOf != null) && (that.arrayOf != null))
    {
    	System.err.println ("ERROR: inconsistent types");
        System.err.println ("this = " + this);
        System.err.println ("that = " + that);
        System.err.println ("this._length_of = " + this.lengthOf);
        System.err.println ("that._array_of = " + that.arrayOf);
        Util.die();
        return;
    }
    if ( (that.lengthOf != null) && (this.arrayOf != null))
    {
    	System.err.println ("ERROR: inconsistent types");
        System.err.println (this);
        System.err.println (that);
        System.err.println ("that._length_of = " + that.lengthOf);
        System.err.println ("this._array_of = " + this.arrayOf);
        Util.die();
        return;
    }

    if (this == that)
    	return;

    if ((this.type != null) && (that.type != null) && !waiveTypeChecking)
    {
   		checkTypes(that);
    }

    // if storing all unif events, add this even if it is in the same equiv class
    UnificationEvent ue = null;
    Util.ASSERT (all_unif_events);
    if (all_unif_events)
    {
        ue = new UnificationEvent (this, that, bcp, depends_on);
        _current_unit_collection.add_event (ue);
        this.addUnificationEvent (ue);
        that.addUnificationEvent (ue);
    }

    super.unify (that);

    WatchList.checkUnify(this, that, bcp);

    // if one is unified with a field, and the other is not, then mark both as unified with fields
    if (markAsConnectedToSingleUnit)
    	this.connectedToSingleUnit = that.connectedToSingleUnit = true;

    if (!((this.type instanceof BasicType) || (that.type instanceof BasicType)))
    {
        if ((this.lengthUnit != null) || (that.lengthUnit != null))
            this.getLengthUnit().realUnify (that.getLengthUnit(), true, false, bcp, ue, markAsConnectedToSingleUnit, false); // no need to waive type checking

        if (unify_array_of_chain)
            if ((this.arrayOf != null) || (that.arrayOf != null))
                this.getArrayOf().realUnify(that.getArrayOf(), true, false, bcp, ue, markAsConnectedToSingleUnit, waiveTypeChecking); // shd we waive type checking here ?
    }

    if (unify_element_of_chain)
    {
    	Util.ASSERT (false, "ShouldNotEnter");
        if (elementOf == null)
        {
            this.elementOf = that.elementOf;
        }
        else if (that.elementOf == null)
        {
            that.elementOf = this.elementOf;
        }
        else
        {
        	if (unify_logger.isLoggable(Level.FINE))
        		unify_logger.fine ("unifying due to element of: " + this.elementOf + " and " + that.elementOf);
            this.elementOf.realUnify (that.elementOf, true, false, bcp, ue, markAsConnectedToSingleUnit, waiveTypeChecking);
        }
    }
}

public void addUnificationEvent (UnificationEvent ue)
{
    if (unifEvents == null)
        unifEvents = new ArrayList();
    unifEvents.add (ue);
}

public Collection<UnificationEvent> getUnificationEvents () { return unifEvents; }

// unifies this with ufo (noting that it happened at the given BCP)
public void unify (UnionFindObject ufo, BCP bcp, boolean waiveTypeChecking)
{
	unificationInProgress = true;
    if (ufo == null)
    {
        return;
    }

    Unit that = (Unit) ufo;
    // sometimes we have spurious unify's e.g.
    // this._field = that._field;
    // will try to unify fields of the same object
    if (this == that)
    	return;

    this.verify ();
    that.verify ();

    realUnify (that, true, false, bcp, null, (this.connectedToSingleUnit || that.connectedToSingleUnit), waiveTypeChecking);
	unificationInProgress = false;
}

public void unify (UnionFindObject ufo, BCP bcp)
{
	unify (ufo, bcp, false);
}

// Unit Invariants
public void verify ()
{
    Util.ASSERT (lengthUnit != this);
    Util.ASSERT (lengthOf != this);
    Util.ASSERT (arrayOf != this);
    Util.ASSERT (elementOf != this);

    // we have subtle equals methods, sometimes x.equals(x) becomes false!
    Util.ASSERT (this.equals(this));

    if (lengthUnit != null)
    {
    	// length unit is attached to an integer, can't be an array nor have a length itself.
        Util.ASSERT (lengthUnit.arrayOf == null);
        Util.ASSERT (lengthUnit.lengthUnit == null);
        // need to verify the following - might not hold when UFO pointers have been reset.
//        if (lengthUnit.lengthOf.find() != this.find())
//        {
//        	System.out.println ("Warning: Unexpected state\nthis = " + this + "\n" + "lengthUnit.lengthOf = " + lengthUnit.lengthOf +
//        					"\n" + "this.find() = " + this.find() + "\n" +
//        					"lengthUnit.lengthOf.find() = " + lengthUnit.lengthOf.find());
//        	Util.ASSERT(false);
//        }
    }

    if (!unificationInProgress)
    {
    	if (elementOf != null)
    		Util.ASSERT (elementOf.arrayOf.find() == this.find());
    	if (arrayOf != null)
    		Util.ASSERT (arrayOf.elementOf.find() == this.find());
    }

    // isGolden true iff goldenId assigned
    Util.ASSERT ((goldenId >= 0) == isGolden);
}

public void appendAttrString (StringBuffer sb)
{
	StringBuilder attr = new StringBuilder();
    NomOrdQuant p = unitAttribs();

    if (p.quantOrOrd())
    	attr.append ("Number ");
    if (p.is_bit_encoded())
    	attr.append ("Bit encoded ");
    if (!p.quantOrOrd() && !p.is_bit_encoded() && p.isEqualityChecked())
    	attr.append ("Nominal ");

    // boolean value = p.quantOrOrd() || p.is_bit_encoded() || p.isEqualityChecked();
    if (p.isDynamicTypeChecked())
    	attr.append ("dynamic type checked ");

    // append attr string only if it has something in it
    if (attr.length() > 0)
    {
    	sb.append (" (");
    	sb.append(attr);
    	sb.append (")");
    }
}

public String toString ()
{
    StringBuffer sb = new StringBuffer ();

    sb.append (this.getClass().getName() + " of type : " + "(" +
               ( (type == null) ? "null" : type.toString ()) +
               ")");
    if (arrayOf != null)
    {
        sb.append (" Array of : " + arrayOf.toString ());
    }


// if we go upstream, it leads to an infinite loop!
//    if (_element_of != null)
//    {
//        sb.append (" Element of : " + _element_of.toString ());
//    }

    return sb.toString ();
}

public boolean isPrimitiveType()
{
    return (type instanceof BasicType);
}

public boolean isStringType ()
{
	return BCELUtil.isStringType(type);
}

public boolean isField()
{
    return (this instanceof FieldUnit);
}

// higher the score, the more stable a unit is
private int getScore()
{
    // NB: score of a PhiUnit should be the lowest!

    if (this instanceof FieldUnit)
        return 5;
    // return values are easier to understand from the method name
    // but deprioritize params and ret vals if they are clones at particular call site
    // absolute numbers below are arbitrary
    if (this instanceof ReturnValueUnit)
    	return ((ReturnValueUnit) this).isClone() ? -1 : 4;
    if (this instanceof MethodParamUnit)
    	return ((MethodParamUnit) this).isClone() ? -1 : 3;
    if (this instanceof ConstantUnit)
        return 2;
    if (this instanceof LocalVarUnit)
        return 1;
    if (this instanceof ArrayLengthUnit)
        return 0;
    if (this instanceof AllocUnit)
        return -1;
    if (this instanceof ElementUnit)
        return -2;
    if (this instanceof CheckcastUnit)
        return -3;
    if (this instanceof MultUnit)
        return -4;
    if (this instanceof PhiUnit)
        return -5;

    Util.die ("Unknown unit type : " + this);
    return 0;
}

public String short_toString() { return toString(); }
public String id_and_string() { return "U" + id + ":" + toString(); }

// note: natural ordering inconsistent with equals
// return -ve number if this is a more important unit than o.
// if other things are equal, lexicographic ordering of
// toString of the objects is used.
// helps print the more important units first
public int compareTo(Object o)
{
    Util.ASSERT (o instanceof Unit);
    Unit other = (Unit) o;

    // first differentiate based on primitives
    if (isPrimitiveType() && !other.isPrimitiveType())
        return -1;
    if (!isPrimitiveType() && other.isPrimitiveType())
        return 1;

    // next differentiate based on strings
    if (isStringType() && !other.isStringType())
        return -1;
    if (!isStringType() && other.isStringType())
        return 1;

    // next differentiate based on whether it is a field, etc.
    // higher score means lower in the order
    int score_diff = other.getScore() - getScore();
    if (score_diff != 0)
        return score_diff;

    return this.toString().compareTo(o.toString());
}

public String toFullString()
{
	if (unifEvents != null)
		return toString() + " [" + unifEvents.size() + " events]";
	else
		return toString();
}

public String toVeryShortString()
{
	return short_toString();
}

}
