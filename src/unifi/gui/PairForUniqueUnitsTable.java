package unifi.gui;

import unifi.units.Unit;
import unifi.util.Util;

public class PairForUniqueUnitsTable {
        public Unit unit; public String displayName;
        public String toString() {
                return (Util.nullOrEmpty(displayName) ? unit.toString() : displayName);
        }
}
