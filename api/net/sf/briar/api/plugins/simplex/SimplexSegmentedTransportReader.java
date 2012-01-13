package net.sf.briar.api.plugins.simplex;

import java.io.IOException;

/**
 * An interface for reading data from a simplex segmented transport. The reader
 * is not responsible for decrypting or authenticating the data before
 * returning it.
 */
public interface SimplexSegmentedTransportReader {

	/**
	 * Reads a frame into the given buffer and returns its length, or -1 if no
	 * more frames can be read.
	 */
	int readFrame(byte[] b) throws IOException;

	/**
	 * Closes the reader and disposes of any associated resources. The first
	 * argument indicates whether the reader is being closed because of an
	 * exception and the second argument indicates whether the connection was
	 * recognised, which may affect how resources are disposed of.
	 */
	void dispose(boolean exception, boolean recognised);
}
