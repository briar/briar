package net.sf.briar.transport;

import java.io.IOException;
import java.io.OutputStream;

interface PacketEncrypter {

	OutputStream getOutputStream();

	void writeTag(byte[] tag) throws IOException;

	void finishPacket() throws IOException;
}
