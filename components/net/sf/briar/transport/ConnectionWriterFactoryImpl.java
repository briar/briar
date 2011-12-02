package net.sf.briar.transport;

import java.io.OutputStream;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Inject;

class ConnectionWriterFactoryImpl implements ConnectionWriterFactory {

	private final CryptoComponent crypto;

	@Inject
	public ConnectionWriterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, ConnectionContext ctx) {
		return createConnectionWriter(out, capacity, true, ctx);
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, ConnectionContext ctx, byte[] tag) {
		// Decrypt the tag
		Cipher tagCipher = crypto.getTagCipher();
		ErasableKey tagKey = crypto.deriveTagKey(ctx.getSecret(), true);
		byte[] iv;
		try {
			tagCipher.init(Cipher.DECRYPT_MODE, tagKey);
			iv = tagCipher.doFinal(tag);
		} catch(BadPaddingException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		}
		tagKey.erase();
		// Validate the tag
		int index = ctx.getTransportIndex().getInt();
		long connection = ctx.getConnectionNumber();
		if(!IvEncoder.validateIv(iv, index, connection))
			throw new IllegalArgumentException();
		return createConnectionWriter(out, capacity, false, ctx);
	}

	private ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, boolean initiator, ConnectionContext ctx) {
		// Derive the keys and erase the secret
		byte[] secret = ctx.getSecret();
		ErasableKey tagKey = crypto.deriveTagKey(secret, initiator);
		ErasableKey frameKey = crypto.deriveFrameKey(secret, initiator);
		ErasableKey macKey = crypto.deriveMacKey(secret, initiator);
		ByteUtils.erase(secret);
		// Create the encrypter
		int index = ctx.getTransportIndex().getInt();
		long connection = ctx.getConnectionNumber();
		byte[] iv = IvEncoder.encodeIv(index, connection);
		Cipher tagCipher = crypto.getTagCipher();
		Cipher frameCipher = crypto.getFrameCipher();
		ConnectionEncrypter encrypter = new ConnectionEncrypterImpl(out,
				capacity, iv, tagCipher, frameCipher, tagKey, frameKey);
		// Create the writer
		Mac mac = crypto.getMac();
		return new ConnectionWriterImpl(encrypter, mac, macKey);
	}
}
