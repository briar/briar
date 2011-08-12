package net.sf.briar.transport;

import java.io.OutputStream;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
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

	public PacketWriter createPacketWriter(OutputStream out, int transportId,
			long connection, byte[] secret) {
		SecretKey macKey = crypto.deriveMacKey(secret);
		SecretKey tagKey = crypto.deriveTagKey(secret);
		SecretKey packetKey = crypto.derivePacketKey(secret);
		Cipher tagCipher = crypto.getTagCipher();
		Cipher packetCipher = crypto.getPacketCipher();
		Mac mac = crypto.getMac();
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		PacketEncrypter encrypter = new PacketEncrypterImpl(out, tagCipher,
				packetCipher, tagKey, packetKey);
		return new PacketWriterImpl(encrypter, mac, transportId, connection);
	}
}
