package org.briarproject.invitation;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/** A connection thread for the peer being Bob in the invitation protocol. */
class BobConnector extends Connector {

	private static final Logger LOG =
			Logger.getLogger(BobConnector.class.getName());

	BobConnector(CryptoComponent crypto,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			AuthorFactory authorFactory, ConnectionManager connectionManager,
			ContactManager contactManager, Clock clock, ConnectorGroup group,
			DuplexPlugin plugin, LocalAuthor localAuthor, PseudoRandom random) {
		super(crypto, bdfReaderFactory, bdfWriterFactory, streamReaderFactory,
				streamWriterFactory, authorFactory,
				connectionManager, contactManager, clock, group, plugin,
				localAuthor, random);
	}

	@Override
	public void run() {
		// Create an incoming or outgoing connection
		DuplexTransportConnection conn = createInvitationConnection(false);
		if (conn == null) return;
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " connected");
		// Carry out the key agreement protocol
		InputStream in;
		OutputStream out;
		BdfReader r;
		BdfWriter w;
		SecretKey master;
		try {
			in = conn.getReader().getInputStream();
			out = conn.getWriter().getOutputStream();
			r = bdfReaderFactory.createReader(in);
			w = bdfWriterFactory.createWriter(out);
			// Alice goes first
			byte[] hash = receivePublicKeyHash(r);
			// Don't proceed with more than one connection
			if (group.getAndSetConnected()) {
				if (LOG.isLoggable(INFO)) LOG.info(pluginName + " redundant");
				tryToClose(conn, false);
				return;
			}
			sendPublicKeyHash(w);
			byte[] key = receivePublicKey(r);
			sendPublicKey(w);
			master = deriveMasterSecret(hash, key, false);
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
		int aliceCode = crypto.deriveBTConfirmationCode(master, true);
		int bobCode = crypto.deriveBTConfirmationCode(master, false);
		group.keyAgreementSucceeded(bobCode, aliceCode);
		// Exchange confirmation results
		boolean localMatched, remoteMatched;
		try {
			remoteMatched = receiveConfirmation(r);
			localMatched = group.waitForLocalConfirmationResult();
			sendConfirmation(w, localMatched);
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
		SecretKey aliceHeaderKey = crypto.deriveBTInvitationKey(master, true);
		SecretKey bobHeaderKey = crypto.deriveBTInvitationKey(master, false);
		// Create the readers
		InputStream streamReader =
				streamReaderFactory.createInvitationStreamReader(in,
						aliceHeaderKey);
		r = bdfReaderFactory.createReader(streamReader);
		// Create the writers
		OutputStream streamWriter =
				streamWriterFactory.createInvitationStreamWriter(out,
						bobHeaderKey);
		w = bdfWriterFactory.createWriter(streamWriter);
		// Derive the nonces
		byte[] aliceNonce = crypto.deriveSignatureNonce(master, true);
		byte[] bobNonce = crypto.deriveSignatureNonce(master, false);
		// Exchange pseudonyms, signed nonces and timestamps
		Author remoteAuthor;
		long remoteTimestamp;
		try {
			remoteAuthor = receivePseudonym(r, aliceNonce);
			remoteTimestamp = receiveTimestamp(r);
			sendPseudonym(w, bobNonce);
			sendTimestamp(w, localTimestamp);
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
		// Add the contact
		try {
			addContact(remoteAuthor, master, timestamp, false);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(conn, true);
			group.pseudonymExchangeFailed();
			return;
		}
		// Reuse the connection as a transport connection
		reuseConnection(conn);
		// Pseudonym exchange succeeded
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " pseudonym exchange succeeded");
		group.pseudonymExchangeSucceeded(remoteAuthor);
	}
}
