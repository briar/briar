package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.Transports;

interface TransportFactory {

	Transports createTransports(Map<String, String> transports, long timestamp);
}
