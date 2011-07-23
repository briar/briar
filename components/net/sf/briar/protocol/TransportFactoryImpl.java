package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.Transports;

class TransportFactoryImpl implements TransportFactory {

	public Transports createTransports(Map<String, String> transports,
			long timestamp) {
		return new TransportsImpl(transports, timestamp);
	}
}
