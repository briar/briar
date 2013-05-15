package net.sf.briar.invitation;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.invitation.InvitationConstants.CONNECTION_TIMEOUT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorFactory;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;

/** A connection thread for the peer being Bob in the invitation protocol. */
class BobConnector extends Connector {

	private static final Logger LOG =
			Logger.getLogger(BobConnector.class.getName());

	BobConnector(CryptoComponent crypto, DatabaseComponent db,
			ReaderFactory readerFactory, WriterFactory writerFactory,
			ConnectionReaderFactory connectionReaderFactory,
			ConnectionWriterFactory connectionWriterFactory,
			AuthorFactory authorFactory, KeyManager keyManager,
			ConnectionDispatcher connectionDispatcher, Clock clock,
			ConnectorGroup group, DuplexPlugin plugin, LocalAuthor localAuthor,
			Map<TransportId, TransportProperties> localProps,
			PseudoRandom random) {
		super(crypto, db, readerFactory, writerFactory, connectionReaderFactory,
				connectionWriterFactory, authorFactory, keyManager,
				connectionDispatcher, clock, group, plugin, localAuthor,
				localProps, random);
	}

	@Override
	public void run() {
		// Try an incoming connection first, then an outgoing connection
		long halfTime = clock.currentTimeMillis() + CONNECTION_TIMEOUT;
		DuplexTransportConnection conn = acceptIncomingConnection();
		if(conn == null) {
			waitForHalfTime(halfTime);
			conn = makeOutgoingConnection();
		}
		if(conn == null) return;
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " connected");
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
			byte[] hash = receivePublicKeyHash(r);
			// Don't proceed with more than one connection
			if(group.getAndSetConnected()) {
				if(LOG.isLoggable(INFO)) LOG.info(pluginName + " redundant");
				tryToClose(conn, false);
				return;
			}
			sendPublicKeyHash(w);
			byte[] key = receivePublicKey(r);
			sendPublicKey(w);
			secret = deriveMasterSecret(hash, key, false);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.keyAgreementFailed();
			tryToClose(conn, true);
			return;
		} catch(GeneralSecurityException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.keyAgreementFailed();
			tryToClose(conn, true);
			return;
		}
		// The key agreement succeeded - derive the confirmation codes
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " agreement succeeded");
		int[] codes = crypto.deriveConfirmationCodes(secret);
		int aliceCode = codes[0], bobCode = codes[1];
		group.keyAgreementSucceeded(bobCode, aliceCode);
		// Exchange confirmation results
		try {
			if(receiveConfirmation(r)) group.remoteConfirmationSucceeded();
			else group.remoteConfirmationFailed();
			sendConfirmation(w);
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
		// The timestamp is taken after exhanging confirmation results
		long localTimestamp = clock.currentTimeMillis();
		// Confirmation succeeded - upgrade to a secure connection
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " confirmation succeeded");
		ConnectionReader connectionReader =
				connectionReaderFactory.createInvitationConnectionReader(in,
						secret, true);
		r = readerFactory.createReader(connectionReader.getInputStream());
		ConnectionWriter connectionWriter =
				connectionWriterFactory.createInvitationConnectionWriter(out,
						secret, false);
		w = writerFactory.createWriter(connectionWriter.getOutputStream());
		// Derive the nonces
		byte[][] nonces = crypto.deriveInvitationNonces(secret);
		byte[] aliceNonce = nonces[0], bobNonce = nonces[1];
		// Exchange pseudonyms, signed nonces, timestamps and transports
		Author remoteAuthor;
		long remoteTimestamp;
		Map<TransportId, TransportProperties> remoteProps;
		try {
			remoteAuthor = receivePseudonym(r, aliceNonce);
			remoteTimestamp = receiveTimestamp(r);
			remoteProps = receiveTransportProperties(r);
			sendPseudonym(w, bobNonce);
			sendTimestamp(w, localTimestamp);
			sendTransportProperties(w);
		} catch(GeneralSecurityException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			group.pseudonymExchangeFailed();
			return;
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			group.pseudonymExchangeFailed();
			return;
		}
		// The epoch is the minimum of the peers' timestamps
		long epoch = Math.min(localTimestamp, remoteTimestamp);
		// Add the contact and store the transports
		try {
			addContact(remoteAuthor, remoteProps, secret, epoch, false);
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			group.pseudonymExchangeFailed();
			return;
		}
		// Pseudonym exchange succeeded
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " pseudonym exchange succeeded");
		group.pseudonymExchangeSucceeded(remoteAuthor);
		// Reuse the connection as an incoming BTP connection
		reuseConnection(conn, false);
	}
}
