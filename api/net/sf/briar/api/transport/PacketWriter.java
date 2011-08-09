package net.sf.briar.api.transport;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A filter that adds tags and MACs to outgoing packets, encrypts them and
 * writes them to the underlying output stream.
 */
public interface PacketWriter {

	/**
	 * Returns the output stream to which packets should be written. (Note that
	 * this is not the underlying output stream.)
	 */
	OutputStream getOutputStream();

	/**
	 * Finishes writing the current packet (if any) and prepares to write the
	 * next packet. If this method is called twice in succession without any
	 * intervening writes, the underlying output stream will be unaffected.
	 */
	void nextPacket() throws IOException;
}
