package net.sf.briar.api;

import java.util.Map;
import java.util.TreeMap;

public class TransportConfig extends TreeMap<String, String> {

	private static final long serialVersionUID = 2330384620787778596L;

	public TransportConfig(Map<String, String> c) {
		super(c);
	}

	public TransportConfig() {
		super();
	}
}
