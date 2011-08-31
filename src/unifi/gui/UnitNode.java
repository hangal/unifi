package unifi.gui;

import unifi.units.Unit;

public class UnitNode {
	Unit u;

	public UnitNode(Unit u) {
		this.u = u;
	}
	public int size() {
		return 1;// +units.size();
	}
	public Unit getUnit() {
		return u;
	}	
	@Override
	public boolean equals(Object other) {
		if (other instanceof UnitNode) {
			if (u.toString().equals(((UnitNode)other).u.toString())) {
				return true;
			}
		}
		return false;
	}	
	@Override
	public int hashCode() {
		return u.toString().hashCode();
	}
}