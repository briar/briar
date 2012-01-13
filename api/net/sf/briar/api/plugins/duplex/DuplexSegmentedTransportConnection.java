package net.sf.briar.api.plugins.duplex;

import net.sf.briar.api.plugins.FrameSink;
import net.sf.briar.api.plugins.FrameSource;

/**
 * An interface for reading and writing data over a duplex segmented transport.
 * The connection is not responsible for encrypting/decrypting or authenticating
 * the data.
 */
public interface DuplexSegmentedTransportConnection extends FrameSource,
FrameSink {

	/**
	 * Returns true if the output stream should be flushed after each packet.
	 */
	boolean shouldFlush();

	/**
	 * Closes the connection and disposes of any associated resources. The
	 * first argument indicates whether the connection is being closed because
	 * of an exception and the second argument indicates whether the connection
	 * was recognised, which may affect how resources are disposed of.
	 */
	void dispose(boolean exception, boolean recognised);
}
