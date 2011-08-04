package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.TransportUpdate;

interface TransportFactory {

	TransportUpdate createTransports(Map<String, Map<String, String>> transports,
			long timestamp);
}
