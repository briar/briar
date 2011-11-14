package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportUpdate;

interface TransportUpdateFactory {

	TransportUpdate createTransportUpdate(Collection<Transport> transports,
			long timestamp);
}
