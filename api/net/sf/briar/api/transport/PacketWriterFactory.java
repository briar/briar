package net.sf.briar.api.transport;

import java.io.OutputStream;

import javax.crypto.SecretKey;

public interface PacketWriterFactory {

	PacketWriter createPacketWriter(OutputStream out, int transportIdentifier,
			long connectionNumber, SecretKey macKey, SecretKey tagKey,
			SecretKey packetKey);
}
