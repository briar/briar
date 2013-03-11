package net.sf.briar.invitation;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.plugins.InvitationConstants.CONNECTION_TIMEOUT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

/** A connection thread for the peer being Alice in the invitation protocol. */
class AliceConnector extends Connector {

	private static final Logger LOG =
			Logger.getLogger(AliceConnector.class.getName());

	AliceConnector(CryptoComponent crypto, ReaderFactory readerFactory,
			WriterFactory writerFactory, Clock clock, ConnectorGroup group,
			DuplexPlugin plugin, int localCode, int remoteCode) {
		super(crypto, readerFactory, writerFactory, clock, group, plugin,
				crypto.getPseudoRandom(localCode, remoteCode));
	}

	@Override
	public void run() {
		// Try an outgoing connection first, then an incoming connection
		long halfTime = clock.currentTimeMillis() + CONNECTION_TIMEOUT;
		DuplexTransportConnection conn = makeOutgoingConnection();
		if(conn == null) {
			waitForHalfTime(halfTime);
			conn = acceptIncomingConnection();
		}
		if(conn == null) return;
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " connected");
		// Don't proceed with more than one connection
		if(group.getAndSetConnected()) {
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
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			return;
		} catch(GeneralSecurityException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			return;
		}
		// The key agreement succeeded - derive the confirmation codes
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " succeeded");
		int[] codes = crypto.deriveConfirmationCodes(secret);
		group.connectionSucceeded(codes[0], codes[1]);
		// Exchange confirmation results
		try {
			sendConfirmation(w);
			if(receiveConfirmation(r)) group.remoteConfirmationSucceeded();
			else group.remoteConfirmationFailed();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			group.remoteConfirmationFailed();
			return;
		} catch(InterruptedException e) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Interrupted while waiting for confirmation");
			tryToClose(conn, true);
			group.remoteConfirmationFailed();
			Thread.currentThread().interrupt();
			return;
		}
		// That's all, folks!
		tryToClose(conn, false);
	}
}