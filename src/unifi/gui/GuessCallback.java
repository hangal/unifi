package unifi.gui;

import java.util.List;
import unifi.UnificationEvent;

import unifi.UnitCollection;
import unifi.units.Unit;

public interface GuessCallback {
	public void graphChanged(String gmlfilename, String repName, UnitCollection uc, Unit u);
	//public void pathFound(List<UnifiEdge> events);
	public void findPathBetweenHulls();
	public void showPathOnly(int flag);
}
