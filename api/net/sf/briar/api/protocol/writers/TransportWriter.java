package net.sf.briar.api.protocol.writers;

import java.io.IOException;
import java.util.Map;

/** An interface for creating a transports update. */
public interface TransportWriter {

	/** Writes the contents of the update. */
	void writeTransports(Map<String, String> transports) throws IOException;
}
