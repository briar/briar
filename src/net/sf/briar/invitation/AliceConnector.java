package net.sf.briar.invitation;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.plugins.InvitationConstants.INVITATION_TIMEOUT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.invitation.ConnectionCallback;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class AliceConnector extends Connector {

	private static final Logger LOG =
			Logger.getLogger(AliceConnector.class.getName());

	AliceConnector(CryptoComponent crypto, ReaderFactory readerFactory,
			WriterFactory writerFactory, DuplexPlugin plugin,
			PseudoRandom random, ConnectionCallback callback,
			AtomicBoolean connected, AtomicBoolean succeeded) {
		super(crypto, readerFactory, writerFactory, plugin, random, callback,
				connected, succeeded);
	}

	@Override
	public void run() {
		// Try an outgoing connection first, then an incoming connection
		long halfTime = System.currentTimeMillis() + INVITATION_TIMEOUT / 2;
		DuplexTransportConnection conn = makeOutgoingConnection();
		if(conn == null) {
			waitForHalfTime(halfTime);
			conn = acceptIncomingConnection();
		}
		if(conn == null) return;
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " connected");
		// Don't proceed with more than one connection
		if(connected.getAndSet(true)) {
			if(LOG.isLoggable(INFO)) LOG.info(pluginName + " redundant");
			tryToClose(conn, false);
			return;
		}
		// Carry out the key agreement protocol
		InputStream in;
		OutputStream out;
		Reader r;
		Writer w;
		byte[] secret;
		try {
			in = conn.getInputStream();
			out = conn.getOutputStream();
			r = readerFactory.createReader(in);
			w = writerFactory.createWriter(out);
			// Alice goes first
			sendPublicKeyHash(w);
			byte[] hash = receivePublicKeyHash(r);
			sendPublicKey(w);
			byte[] key = receivePublicKey(r);
			secret = deriveSharedSecret(hash, key, true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			tryToClose(conn, true);
			return;
		} catch(GeneralSecurityException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			tryToClose(conn, true);
			return;
		}
		// The key agreement succeeded
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " succeeded");
		succeeded.set(true);
		// Derive the confirmation codes
		int[] codes = crypto.deriveConfirmationCodes(secret);
		callback.connectionEstablished(codes[0], codes[1],
				new ConfirmationSender(w));
		// Check whether the remote peer's confirmation codes matched
		try {
			if(r.readBoolean()) callback.codesMatch();
			else callback.codesDoNotMatch();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			tryToClose(conn, true);
			callback.codesDoNotMatch();
		}
	}
}