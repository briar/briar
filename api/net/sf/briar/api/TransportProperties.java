package net.sf.briar.api;

import java.util.Map;
import java.util.TreeMap;

public class TransportProperties extends TreeMap<String, String> {

	private static final long serialVersionUID = 7533739534204953625L;

	public TransportProperties(Map<String, String> p) {
		super(p);
	}

	public TransportProperties() {
		super();
	}
}
