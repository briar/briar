package net.sf.briar.api;

import java.util.Hashtable;
import java.util.Map;

public class TransportConfig extends Hashtable<String, String> {

	private static final long serialVersionUID = 2330384620787778596L;

	public TransportConfig(Map<String, String> c) {
		super(c);
	}

	public TransportConfig() {
		super();
	}
}
