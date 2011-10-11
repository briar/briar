package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.protocol.TransportUpdate;

class TransportUpdateImpl implements TransportUpdate {

	private final Map<TransportId, TransportProperties> transports;
	private final long timestamp;

	TransportUpdateImpl(Map<TransportId, TransportProperties> transports,
			long timestamp) {
		this.transports = transports;
		this.timestamp = timestamp;
	}

	public Map<TransportId, TransportProperties> getTransports() {
		return transports;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
