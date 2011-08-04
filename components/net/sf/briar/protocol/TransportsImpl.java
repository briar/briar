package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.TransportUpdate;

class TransportsImpl implements TransportUpdate {

	private final Map<String, Map<String, String>> transports;
	private final long timestamp;

	TransportsImpl(Map<String, Map<String, String>> transports,
			long timestamp) {
		this.transports = transports;
		this.timestamp = timestamp;
	}

	public Map<String, Map<String, String>> getTransports() {
		return transports;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
