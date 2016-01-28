package org.briarproject.api.properties;

import org.briarproject.api.StringMap;

import java.util.Map;

public class TransportProperties extends StringMap {

	private static final long serialVersionUID = 7533739534204953625L;

	public TransportProperties(Map<String, String> m) {
		super(m);
	}

	public TransportProperties() {
		super();
	}
}
