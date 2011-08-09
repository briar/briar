package net.sf.briar.transport;

import java.io.OutputStream;
import java.security.InvalidKeyException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.transport.PacketWriter;
import net.sf.briar.api.transport.PacketWriterFactory;

import com.google.inject.Inject;

class PacketWriterFactoryImpl implements PacketWriterFactory {

	private final CryptoComponent crypto;

	@Inject
	public PacketWriterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public PacketWriter createPacketWriter(OutputStream out,
			int transportIdentifier, long connectionNumber, SecretKey macKey,
			SecretKey tagKey, SecretKey packetKey) {
		Mac mac = crypto.getMac();
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		PacketEncrypter e = new PacketEncrypterImpl(out, crypto.getTagCipher(),
				crypto.getPacketCipher(), tagKey, packetKey);
		return new PacketWriterImpl(e, mac, transportIdentifier,
				connectionNumber);
	}
}
