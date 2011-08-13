package net.sf.briar.api.transport;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

/**
 * Reads encrypted packets from an underlying input stream, decrypts and
 * authenticates them.
 */
public interface PacketReader {

	/**
	 * Returns the input stream from which packets should be read. (Note that
	 * this is not the underlying input stream.)
	 */
	InputStream getInputStream();

	/**
	 * Finishes reading the current packet (if any), authenticates the packet
	 * and prepares to read the next packet. If this method is called twice in
	 * succession without any intervening reads, the underlying input stream
	 * will be unaffected.
	 */
	void finishPacket() throws IOException, GeneralSecurityException;
}
