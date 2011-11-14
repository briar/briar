package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportUpdate;

class TransportUpdateImpl implements TransportUpdate {

	private final Collection<Transport> transports;
	private final long timestamp;

	TransportUpdateImpl(Collection<Transport> transports,
			long timestamp) {
		this.transports = transports;
		this.timestamp = timestamp;
	}

	public Collection<Transport> getTransports() {
		return transports;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
