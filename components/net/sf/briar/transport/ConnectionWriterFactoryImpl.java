package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.OutputStream;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;

import com.google.inject.Inject;

class ConnectionWriterFactoryImpl implements ConnectionWriterFactory {

	private final CryptoComponent crypto;

	@Inject
	public ConnectionWriterFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionWriter createConnectionWriter(OutputStream out,
			long capacity, ConnectionContext ctx, boolean initiator) {
		byte[] secret = ctx.getSecret();
		long connection = ctx.getConnectionNumber();
		boolean alice = ctx.getAlice();
		ErasableKey frameKey = crypto.deriveFrameKey(secret, connection, alice,
				initiator);
		FrameWriter encryption;
		if(initiator) {
			encryption = new OutgoingEncryptionLayer(out, capacity,
					crypto.getFrameCipher(), frameKey, MAX_FRAME_LENGTH,
					ctx.getTag());
		} else {
			encryption = new OutgoingEncryptionLayer(out, capacity,
					crypto.getFrameCipher(), frameKey, MAX_FRAME_LENGTH);
		}
		return new ConnectionWriterImpl(encryption, MAX_FRAME_LENGTH);
	}
}