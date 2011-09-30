package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;

class TransportUpdateImpl implements TransportUpdate {

	private final Map<TransportId, Map<String, String>> transports;
	private final long timestamp;

	TransportUpdateImpl(Map<TransportId, Map<String, String>> transports,
			long timestamp) {
		this.transports = transports;
		this.timestamp = timestamp;
	}

	public Map<TransportId, Map<String, String>> getTransports() {
		return transports;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
