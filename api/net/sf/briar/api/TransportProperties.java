package net.sf.briar.api;

import java.util.Hashtable;
import java.util.Map;

public class TransportProperties extends Hashtable<String, String> {

	private static final long serialVersionUID = 7533739534204953625L;

	public TransportProperties(Map<String, String> p) {
		super(p);
	}

	public TransportProperties() {
		super();
	}
}
