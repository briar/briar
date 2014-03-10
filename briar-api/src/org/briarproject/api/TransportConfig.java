package org.briarproject.api;

import java.util.Map;

public class TransportConfig extends StringMap {

	private static final long serialVersionUID = 2330384620787778596L;

	public TransportConfig(Map<String, String> m) {
		super(m);
	}

	public TransportConfig() {
		super();
	}
}
