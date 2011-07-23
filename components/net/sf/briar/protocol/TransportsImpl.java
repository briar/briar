package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.Transports;

class TransportsImpl implements Transports {

	private final Map<String, String> transports;
	private final long timestamp;

	TransportsImpl(Map<String, String> transports, long timestamp) {
		this.transports = transports;
		this.timestamp = timestamp;
	}

	public Map<String, String> getTransports() {
		return transports;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
