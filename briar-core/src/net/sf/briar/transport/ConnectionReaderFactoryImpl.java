package net.sf.briar.transport;

import java.io.InputStream;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;

import com.google.inject.Inject;

class ConnectionReaderFactoryImpl implements ConnectionReaderFactory {

	private final CryptoComponent crypto;

	@Inject
	ConnectionReaderFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionReader createConnectionReader(InputStream in,
			int maxFrameLength, ConnectionContext ctx, boolean incoming,
			boolean initiator) {
		byte[] secret = ctx.getSecret();
		long connection = ctx.getConnectionNumber();
		boolean weAreAlice = ctx.getAlice();
		boolean initiatorIsAlice = incoming ? !weAreAlice : weAreAlice;
		ErasableKey frameKey = crypto.deriveFrameKey(secret, connection,
				initiatorIsAlice, initiator);
		FrameReader encryption = new IncomingEncryptionLayer(in,
				crypto.getFrameCipher(), frameKey, maxFrameLength);
		return new ConnectionReaderImpl(encryption, maxFrameLength);
	}

	public ConnectionReader createInvitationConnectionReader(InputStream in,
			int maxFrameLength, byte[] secret, boolean alice) {
		ErasableKey frameKey = crypto.deriveFrameKey(secret, 0, true, alice);
		FrameReader encryption = new IncomingEncryptionLayer(in,
				crypto.getFrameCipher(), frameKey, maxFrameLength);
		return new ConnectionReaderImpl(encryption, maxFrameLength);
	}
}
