package net.sf.briar.transport;

import java.io.InputStream;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Inject;

class ConnectionReaderFactoryImpl implements ConnectionReaderFactory {

	private final CryptoComponent crypto;

	@Inject
	ConnectionReaderFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionReader createConnectionReader(InputStream in,
			ConnectionContext ctx, byte[] tag) {
		// Decrypt the tag
		Cipher tagCipher = crypto.getTagCipher();
		ErasableKey tagKey = crypto.deriveTagKey(ctx.getSecret(), true);
		byte[] iv;
		try {
			tagCipher.init(Cipher.DECRYPT_MODE, tagKey);
			iv = tagCipher.doFinal(tag);
		} catch(BadPaddingException badCipher) {
			throw new IllegalArgumentException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new IllegalArgumentException(badCipher);
		} catch(InvalidKeyException badKey) {
			throw new IllegalArgumentException(badKey);
		}
		tagKey.erase();
		// Validate the tag
		int index = ctx.getTransportIndex().getInt();
		long connection = ctx.getConnectionNumber();
		if(!IvEncoder.validateIv(iv, index, connection))
			throw new IllegalArgumentException();
		return createConnectionReader(in, true, ctx);
	}

	public ConnectionReader createConnectionReader(InputStream in,
			ConnectionContext ctx) {
		return createConnectionReader(in, false, ctx);
	}

	private ConnectionReader createConnectionReader(InputStream in,
			boolean initiator, ConnectionContext ctx) {
		// Derive the keys and erase the secret
		byte[] secret = ctx.getSecret();
		ErasableKey frameKey = crypto.deriveFrameKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the decrypter
		int index = ctx.getTransportIndex().getInt();
		long connection = ctx.getConnectionNumber();
		byte[] iv = IvEncoder.encodeIv(index, connection);
		Cipher frameCipher = crypto.getFrameCipher();
		ConnectionDecrypter decrypter = new ConnectionDecrypterImpl(in, iv,
				frameCipher, frameKey);
		// Create the reader
		Mac mac = crypto.getMac();
		return new ConnectionReaderImpl(decrypter, mac, macKey);
	}
}
