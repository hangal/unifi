package unifi.gui;

import java.util.ArrayList;
import java.util.List;

import unifi.GoldenUnifiEvent;
import unifi.UnificationEvent;
import unifi.units.Unit;

public class SuperEdge extends UnifiEdge {

	public List<UnificationEvent> events;
	public List<Unit> units;
	SuperEdge(UnifiEdge e1, UnifiEdge e2, UnitNode n) {
		super(e1.e);
		events = new ArrayList<UnificationEvent>();
		units = new ArrayList<Unit>();
		if (e1 instanceof SuperEdge) {
			SuperEdge se = (SuperEdge)e1;
			events.addAll(se.events);
			units.addAll(se.units);
		} else {
			events.add(e1.e);
		}
		
		if (e2 instanceof SuperEdge) {
			SuperEdge se = (SuperEdge)e2;
			events.addAll(se.events);
			units.addAll(se.units);
		} else {
			events.add(e2.e);			
		}

		units.add(n.u);
	}
	
	@Override
	public int size() {
		return events.size();
	}
	
	@Override
	public boolean isGolden() {
		for (UnificationEvent e:events) {
			if (e instanceof GoldenUnifiEvent)
				return true;
		}
		return false;
	}
}