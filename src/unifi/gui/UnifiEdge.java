package unifi.gui;

import org.jgrapht.graph.DefaultEdge;

import unifi.GoldenUnifiEvent;
import unifi.UnificationEvent;

public class UnifiEdge extends DefaultEdge {
	public UnificationEvent e;
	public int guessEdgeId;

	UnifiEdge(UnificationEvent e) {
		this.e = e;
	}
	public String toString() {
		String str = e.toString();
		return str;
	}
	public int size() {
		return 1;
	}
	public boolean isGolden() {
		return e instanceof GoldenUnifiEvent;
	}	
}