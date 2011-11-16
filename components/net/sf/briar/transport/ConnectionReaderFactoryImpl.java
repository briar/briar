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
			ConnectionContext ctx, byte[] encryptedIv) {
		// Decrypt the IV
		Cipher ivCipher = crypto.getIvCipher();
		ErasableKey ivKey = crypto.deriveIvKey(ctx.getSecret(), true);
		byte[] iv;
		try {
			ivCipher.init(Cipher.DECRYPT_MODE, ivKey);
			iv = ivCipher.doFinal(encryptedIv);
		} catch(BadPaddingException badCipher) {
			throw new IllegalArgumentException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new IllegalArgumentException(badCipher);
		} catch(InvalidKeyException badKey) {
			throw new IllegalArgumentException(badKey);
		}
		ivKey.erase();
		// Validate the IV
		if(!IvEncoder.validateIv(iv, true, ctx))
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
		byte[] iv = IvEncoder.encodeIv(initiator, ctx);
		Cipher frameCipher = crypto.getFrameCipher();
		ConnectionDecrypter decrypter = new ConnectionDecrypterImpl(in, iv,
				frameCipher, frameKey);
		// Create the reader
		Mac mac = crypto.getMac();
		return new ConnectionReaderImpl(decrypter, mac, macKey);
	}
}
