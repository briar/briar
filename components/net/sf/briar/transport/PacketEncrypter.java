package net.sf.briar.transport;

import java.io.IOException;
import java.io.OutputStream;

interface PacketEncrypter {

	/** Returns the output stream to which packets should be written. */
	OutputStream getOutputStream();

	/** Encrypts the given tag and writes it to the underlying output stream. */
	void writeTag(byte[] tag) throws IOException;

	/** Finishes writing the current packet. */
	void finishPacket() throws IOException;
}
