package unifi;

import java.util.List;

import unifi.units.Unit;

public class GoldenUnifiEvent extends UnificationEvent {

	private Unit rep;
	private List<Unit> goldenUnits;

	public GoldenUnifiEvent(Unit rep, List<Unit> units) {
		super(units.get(0), units.get(1), null);
		this.rep = rep;
		goldenUnits = units;
	}

	public String toString() {
		return "Golden Unification event for " + rep;
	}

	public List<Unit> get_units() {
		return goldenUnits;
	}
}
