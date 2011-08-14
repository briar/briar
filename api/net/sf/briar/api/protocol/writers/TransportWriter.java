package net.sf.briar.api.protocol.writers;

import java.io.IOException;
import java.util.Map;

/** An interface for creating a transport update. */
public interface TransportWriter {

	/** Writes the contents of the update. */
	void writeTransports(Map<String, Map<String, String>> transports,
			long timestamp) throws IOException;
}
