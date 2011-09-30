package net.sf.briar.api.transport;

import net.sf.briar.api.TransportId;

public interface ConnectionRecogniserFactory {

	ConnectionRecogniser createConnectionRecogniser(TransportId t);
}
