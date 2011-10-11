package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.protocol.TransportUpdate;

class TransportFactoryImpl implements TransportFactory {

	public TransportUpdate createTransportUpdate(
			Map<TransportId, TransportProperties> transports, long timestamp) {
		return new TransportUpdateImpl(transports, timestamp);
	}
}
