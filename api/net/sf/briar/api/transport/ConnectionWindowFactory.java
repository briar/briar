package net.sf.briar.api.transport;

import java.util.Map;

import net.sf.briar.api.protocol.TransportIndex;

public interface ConnectionWindowFactory {

	ConnectionWindow createConnectionWindow(TransportIndex i, byte[] secret);

	ConnectionWindow createConnectionWindow(TransportIndex i,
			Map<Long, byte[]> unseen);
}
