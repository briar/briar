package org.briarproject.api.transport;

import org.briarproject.api.TransportId;

public interface TransportKeyManagerFactory {

	TransportKeyManager createTransportKeyManager(TransportId transportId,
			long maxLatency);

}
