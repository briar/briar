package net.sf.briar.transport;

import java.io.InputStream;

import javax.crypto.Cipher;
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
		// Validate the tag
		Cipher tagCipher = crypto.getTagCipher();
		ErasableKey tagKey = crypto.deriveTagKey(ctx.getSecret(), true);
		boolean valid = TagEncoder.validateTag(tag, 0, tagCipher, tagKey);
		tagKey.erase();
		if(!valid) throw new IllegalArgumentException();
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
		Cipher frameCipher = crypto.getFrameCipher();
		ConnectionDecrypter decrypter = new ConnectionDecrypterImpl(in,
				frameCipher, frameKey);
		// Create the reader
		Mac mac = crypto.getMac();
		return new ConnectionReaderImpl(decrypter, mac, macKey);
	}
}
