package org.briarproject.transport;

import org.briarproject.api.TransportId;

interface TransportKeyManagerFactory {

	TransportKeyManager createTransportKeyManager(TransportId transportId,
			long maxLatency);

}
