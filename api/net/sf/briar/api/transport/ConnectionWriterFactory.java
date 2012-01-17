package net.sf.briar.api.transport;

import java.io.OutputStream;

import net.sf.briar.api.plugins.SegmentSink;

public interface ConnectionWriterFactory {

	/**
	 * Creates a connection writer for a simplex connection or one side of a
	 * duplex connection. The secret is erased before this method returns.
	 */
	ConnectionWriter createConnectionWriter(OutputStream out, long capacity,
			byte[] secret, boolean initiator);

	/**
	 * Creates a connection writer for a simplex connection or one side of a
	 * duplex connection. The secret is erased before this method returns.
	 */
	ConnectionWriter createConnectionWriter(SegmentSink out, long capacity,
			byte[] secret, boolean initiator);
}
