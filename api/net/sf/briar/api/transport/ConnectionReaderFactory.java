package net.sf.briar.api.transport;

import java.io.InputStream;

import net.sf.briar.api.plugins.SegmentSource;

public interface ConnectionReaderFactory {

	/**
	 * Creates a connection reader for a simplex connection or the initiator's
	 * side of a duplex connection. The secret is erased before this method
	 * returns.
	 */
	ConnectionReader createConnectionReader(InputStream in, byte[] secret,
			byte[] bufferedTag);

	/**
	 * Creates a connection reader for a simplex connection or the initiator's
	 * side of a duplex connection. The secret is erased before this method
	 * returns.
	 */
	ConnectionReader createConnectionReader(SegmentSource in, byte[] secret,
			Segment bufferedSegment);

	/**
	 * Creates a connection reader for the responder's side of a duplex
	 * connection. The secret is erased before this method returns.
	 */
	ConnectionReader createConnectionReader(InputStream in, byte[] secret);

	/**
	 * Creates a connection reader for the responder's side of a duplex
	 * connection. The secret is erased before this method returns.
	 */
	ConnectionReader createConnectionReader(SegmentSource in, byte[] secret);
}
