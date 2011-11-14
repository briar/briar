package net.sf.briar.api.protocol.writers;

import java.io.IOException;
import java.util.Collection;

import net.sf.briar.api.protocol.Transport;

/** An interface for creating a transport update. */
public interface TransportUpdateWriter {

	/** Writes the contents of the update. */
	void writeTransports(Collection<Transport> transports, long timestamp)
	throws IOException;
}
