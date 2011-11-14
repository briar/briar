package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportUpdate;

class TransportUpdateFactoryImpl implements TransportUpdateFactory {

	public TransportUpdate createTransportUpdate(
			Collection<Transport> transports, long timestamp) {
		return new TransportUpdateImpl(transports, timestamp);
	}
}
