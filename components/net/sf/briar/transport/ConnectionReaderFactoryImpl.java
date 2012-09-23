package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

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
			ConnectionContext ctx, boolean initiator) {
		byte[] secret = ctx.getSecret();
		long connection = ctx.getConnectionNumber();
		boolean alice = ctx.getAlice();
		ErasableKey frameKey = crypto.deriveFrameKey(secret, connection, alice,
				initiator);
		FrameReader encryption = new IncomingEncryptionLayer(in,
				crypto.getFrameCipher(), frameKey, MAX_FRAME_LENGTH);
		return new ConnectionReaderImpl(encryption, MAX_FRAME_LENGTH);
	}
}
