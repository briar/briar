package org.briarproject.api.transport;

import java.io.OutputStream;

public interface ConnectionWriterFactory {

	/** Creates a connection writer for one side of a connection. */
	ConnectionWriter createConnectionWriter(OutputStream out,
			int maxFrameLength, long capacity, ConnectionContext ctx,
			boolean incoming, boolean initiator);

	/** Creates a connection writer for one side of an invitation connection. */
	ConnectionWriter createInvitationConnectionWriter(OutputStream out,
			int maxFrameLength, byte[] secret, boolean alice);
}
