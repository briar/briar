package net.sf.briar.api.protocol.writers;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;

/** An interface for creating a transport update. */
public interface TransportWriter {

	/** Writes the contents of the update. */
	void writeTransports(Map<TransportId, TransportProperties> transports,
			long timestamp) throws IOException;
}
