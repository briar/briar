package org.briarproject.invitation;

import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.data.Reader;
import org.briarproject.api.data.ReaderFactory;
import org.briarproject.api.data.Writer;
import org.briarproject.api.data.WriterFactory;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.property.TransportPropertyManager;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/** A connection thread for the peer being Alice in the invitation protocol. */
class AliceConnector extends Connector {

	private static final Logger LOG =
			Logger.getLogger(AliceConnector.class.getName());

	AliceConnector(CryptoComponent crypto,
			ReaderFactory readerFactory, WriterFactory writerFactory,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			AuthorFactory authorFactory, GroupFactory groupFactory,
			KeyManager keyManager, ConnectionManager connectionManager,
			ContactManager contactManager, MessagingManager messagingManager,
			TransportPropertyManager transportPropertyManager, Clock clock,
			boolean reuseConnection, ConnectorGroup group, DuplexPlugin plugin,
			LocalAuthor localAuthor,
			Map<TransportId, TransportProperties> localProps,
			PseudoRandom random) {
		super(crypto, readerFactory, writerFactory, streamReaderFactory,
				streamWriterFactory, authorFactory, groupFactory,
				keyManager, connectionManager, contactManager,
				messagingManager, transportPropertyManager, clock,
				reuseConnection, group, plugin, localAuthor, localProps,
				random);
	}

	@Override
	public void run() {
		// Create an incoming or outgoing connection
		DuplexTransportConnection conn = createInvitationConnection();
		if (conn == null) return;
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " connected");
		// Don't proceed with more than one connection
		if (group.getAndSetConnected()) {
			if (LOG.isLoggable(INFO)) LOG.info(pluginName + " redundant");
			tryToClose(conn, false);
			return;
		}
		// Carry out the key agreement protocol
		InputStream in;
		OutputStream out;
		Reader r;
		Writer w;
		SecretKey master;
		try {
			in = conn.getReader().getInputStream();
			out = conn.getWriter().getOutputStream();
			r = readerFactory.createReader(in);
			w = writerFactory.createWriter(out);
			// Alice goes first
			sendPublicKeyHash(w);
			byte[] hash = receivePublicKeyHash(r);
			sendPublicKey(w);
			byte[] key = receivePublicKey(r);
			master = deriveMasterSecret(hash, key, true);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.keyAgreementFailed();
			tryToClose(conn, true);
			return;
		} catch (GeneralSecurityException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.keyAgreementFailed();
			tryToClose(conn, true);
			return;
		}
		// The key agreement succeeded - derive the confirmation codes
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " agreement succeeded");
		int aliceCode = crypto.deriveConfirmationCode(master, true);
		int bobCode = crypto.deriveConfirmationCode(master, false);
		group.keyAgreementSucceeded(aliceCode, bobCode);
		// Exchange confirmation results
		boolean localMatched, remoteMatched;
		try {
			localMatched = group.waitForLocalConfirmationResult();
			sendConfirmation(w, localMatched);
			remoteMatched = receiveConfirmation(r);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.remoteConfirmationFailed();
			tryToClose(conn, true);
			return;
		} catch (InterruptedException e) {
			LOG.warning("Interrupted while waiting for confirmation");
			group.remoteConfirmationFailed();
			tryToClose(conn, true);
			Thread.currentThread().interrupt();
			return;
		}
		if (remoteMatched) group.remoteConfirmationSucceeded();
		else group.remoteConfirmationFailed();
		if (!(localMatched && remoteMatched)) {
			if (LOG.isLoggable(INFO))
				LOG.info(pluginName + " confirmation failed");
			tryToClose(conn, false);
			return;
		}
		// The timestamp is taken after exchanging confirmation results
		long localTimestamp = clock.currentTimeMillis();
		// Confirmation succeeded - upgrade to a secure connection
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " confirmation succeeded");
		// Derive the header keys
		SecretKey aliceHeaderKey = crypto.deriveInvitationKey(master, true);
		SecretKey bobHeaderKey = crypto.deriveInvitationKey(master, false);
		// Create the readers
		InputStream streamReader =
				streamReaderFactory.createInvitationStreamReader(in,
						bobHeaderKey);
		r = readerFactory.createReader(streamReader);
		// Create the writers
		OutputStream streamWriter =
				streamWriterFactory.createInvitationStreamWriter(out,
						aliceHeaderKey);
		w = writerFactory.createWriter(streamWriter);
		// Derive the invitation nonces
		byte[] aliceNonce = crypto.deriveSignatureNonce(master, true);
		byte[] bobNonce = crypto.deriveSignatureNonce(master, false);
		// Exchange pseudonyms, signed nonces, timestamps and transports
		Author remoteAuthor;
		long remoteTimestamp;
		Map<TransportId, TransportProperties> remoteProps;
		boolean remoteReuseConnection;
		try {
			sendPseudonym(w, aliceNonce);
			sendTimestamp(w, localTimestamp);
			sendTransportProperties(w);
			sendConfirmation(w, reuseConnection);
			remoteAuthor = receivePseudonym(r, bobNonce);
			remoteTimestamp = receiveTimestamp(r);
			remoteProps = receiveTransportProperties(r);
			remoteReuseConnection = receiveConfirmation(r);
			// Close the outgoing stream and expect EOF on the incoming stream
			w.close();
			if (!r.eof()) LOG.warning("Unexpected data at end of connection");
		} catch (GeneralSecurityException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.pseudonymExchangeFailed();
			tryToClose(conn, true);
			return;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.pseudonymExchangeFailed();
			tryToClose(conn, true);
			return;
		}
		// The agreed timestamp is the minimum of the peers' timestamps
		long timestamp = Math.min(localTimestamp, remoteTimestamp);
		// Add the contact and store the transports
		try {
			addContact(remoteAuthor, remoteProps, master, timestamp, true);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			group.pseudonymExchangeFailed();
			return;
		}
		// Reuse the connection as a transport connection if both peers agree
		if (reuseConnection && remoteReuseConnection) reuseConnection(conn);
		else tryToClose(conn, false);
		// Pseudonym exchange succeeded
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " pseudonym exchange succeeded");
		group.pseudonymExchangeSucceeded(remoteAuthor);
	}
}
