package net.sf.briar.transport;

import java.io.InputStream;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.transport.PacketReader;
import net.sf.briar.api.transport.PacketReaderFactory;

import com.google.inject.Inject;

class PacketReaderFactoryImpl implements PacketReaderFactory {

	private final CryptoComponent crypto;

	@Inject
	PacketReaderFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public PacketReader createPacketReader(byte[] firstTag, InputStream in,
			int transportId, long connection, byte[] secret) {
		SecretKey macKey = crypto.deriveIncomingMacKey(secret);
		SecretKey tagKey = crypto.deriveIncomingTagKey(secret);
		SecretKey packetKey = crypto.deriveIncomingPacketKey(secret);
		Cipher tagCipher = crypto.getTagCipher();
		Cipher packetCipher = crypto.getPacketCipher();
		Mac mac = crypto.getMac();
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		PacketDecrypter decrypter = new PacketDecrypterImpl(firstTag, in,
				tagCipher, packetCipher, tagKey, packetKey);
		return new PacketReaderImpl(decrypter, mac, transportId, connection);
	}
}
