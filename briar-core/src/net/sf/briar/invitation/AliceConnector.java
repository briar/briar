package net.sf.briar.invitation;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

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

/** A connection thread for the peer being Alice in the invitation protocol. */
class AliceConnector extends Connector {

	private static final Logger LOG =
			Logger.getLogger(AliceConnector.class.getName());

	AliceConnector(CryptoComponent crypto, DatabaseComponent db,
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
		// Create an incoming or outgoing connection
		DuplexTransportConnection conn = createInvitationConnection();
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
			secret = deriveMasterSecret(hash, key, true);
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
		group.keyAgreementSucceeded(aliceCode, bobCode);
		// Exchange confirmation results
		boolean localMatched, remoteMatched;
		try {
			localMatched = group.waitForLocalConfirmationResult();
			sendConfirmation(w, localMatched);
			remoteMatched = receiveConfirmation(r);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.remoteConfirmationFailed();
			tryToClose(conn, true);
			return;
		} catch(InterruptedException e) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Interrupted while waiting for confirmation");
			group.remoteConfirmationFailed();
			tryToClose(conn, true);
			Thread.currentThread().interrupt();
			return;
		}
		if(remoteMatched) group.remoteConfirmationSucceeded();
		else group.remoteConfirmationFailed();
		if(!(localMatched && remoteMatched)) {
			tryToClose(conn, false);
			return;
		}
		// The timestamp is taken after exhanging confirmation results
		long localTimestamp = clock.currentTimeMillis();
		// Confirmation succeeded - upgrade to a secure connection
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " confirmation succeeded");
		int maxFrameLength = conn.getMaxFrameLength();
		ConnectionReader connectionReader =
				connectionReaderFactory.createInvitationConnectionReader(in,
						maxFrameLength, secret, false);
		r = readerFactory.createReader(connectionReader.getInputStream());
		ConnectionWriter connectionWriter =
				connectionWriterFactory.createInvitationConnectionWriter(out,
						maxFrameLength, secret, true);
		w = writerFactory.createWriter(connectionWriter.getOutputStream());
		// Derive the invitation nonces
		byte[][] nonces = crypto.deriveInvitationNonces(secret);
		byte[] aliceNonce = nonces[0], bobNonce = nonces[1];
		// Exchange pseudonyms, signed nonces, timestamps and transports
		Author remoteAuthor;
		long remoteTimestamp;
		Map<TransportId, TransportProperties> remoteProps;
		try {
			sendPseudonym(w, aliceNonce);
			sendTimestamp(w, localTimestamp);
			sendTransportProperties(w);
			remoteAuthor = receivePseudonym(r, bobNonce);
			remoteTimestamp = receiveTimestamp(r);
			remoteProps = receiveTransportProperties(r);
		} catch(GeneralSecurityException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.pseudonymExchangeFailed();
			tryToClose(conn, true);
			return;
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			group.pseudonymExchangeFailed();
			tryToClose(conn, true);
			return;
		}
		// The epoch is the minimum of the peers' timestamps
		long epoch = Math.min(localTimestamp, remoteTimestamp);
		// Add the contact and store the transports
		try {
			addContact(remoteAuthor, remoteProps, secret, epoch, true);
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
		// Reuse the connection as an outgoing BTP connection
		reuseConnection(conn, true);
	}
}