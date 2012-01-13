package net.sf.briar.api.plugins.duplex;

import java.io.IOException;

/**
 * An interface for reading and writing data over a duplex segmented transport.
 * The connection is not responsible for encrypting/decrypting or authenticating
 * the data.
 */
public interface DuplexSegmentedTransportConnection {

	/**
	 * Reads a frame into the given buffer and returns its length, or -1 if no
	 * more frames can be read.
	 */
	int readFrame(byte[] b) throws IOException;

	/** Writes the given frame to the transport. */
	void writeFrame(byte[] b, int len) throws IOException;

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
