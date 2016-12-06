package org.briarproject.bramble.invitation;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactExchangeTask;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PseudoRandom;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.BdfWriter;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.invitation.InvitationConstants.CONNECTION_TIMEOUT;

// FIXME: This class has way too many dependencies
@NotNullByDefault
abstract class Connector extends Thread {

	private static final Logger LOG =
			Logger.getLogger(Connector.class.getName());
	private static final String LABEL_PUBLIC_KEY =
			"org.briarproject.bramble.invitation.PUBLIC_KEY";

	protected final CryptoComponent crypto;
	protected final BdfReaderFactory bdfReaderFactory;
	protected final BdfWriterFactory bdfWriterFactory;
	protected final ContactExchangeTask contactExchangeTask;
	protected final ConnectorGroup group;
	protected final DuplexPlugin plugin;
	protected final LocalAuthor localAuthor;
	protected final PseudoRandom random;
	protected final String pluginName;

	private final KeyPair keyPair;
	private final KeyParser keyParser;

	Connector(CryptoComponent crypto, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory,
			ContactExchangeTask contactExchangeTask, ConnectorGroup group,
			DuplexPlugin plugin, LocalAuthor localAuthor, PseudoRandom random) {
		super("Connector");
		this.crypto = crypto;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.contactExchangeTask = contactExchangeTask;
		this.group = group;
		this.plugin = plugin;
		this.localAuthor = localAuthor;
		this.random = random;
		pluginName = plugin.getClass().getName();
		keyPair = crypto.generateAgreementKeyPair();
		keyParser = crypto.getAgreementKeyParser();
	}

	@Nullable
	DuplexTransportConnection createInvitationConnection(boolean alice) {
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " creating invitation connection");
		return plugin.createInvitationConnection(random, CONNECTION_TIMEOUT,
				alice);
	}

	void sendPublicKeyHash(BdfWriter w) throws IOException {
		byte[] hash =
				crypto.hash(LABEL_PUBLIC_KEY, keyPair.getPublic().getEncoded());
		w.writeRaw(hash);
		w.flush();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " sent hash");
	}

	byte[] receivePublicKeyHash(BdfReader r) throws IOException {
		int hashLength = crypto.getHashLength();
		byte[] b = r.readRaw(hashLength);
		if (b.length < hashLength) throw new FormatException();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " received hash");
		return b;
	}

	void sendPublicKey(BdfWriter w) throws IOException {
		byte[] key = keyPair.getPublic().getEncoded();
		w.writeRaw(key);
		w.flush();
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " sent key");
	}

	byte[] receivePublicKey(BdfReader r)
			throws GeneralSecurityException, IOException {
		byte[] b = r.readRaw(MAX_PUBLIC_KEY_LENGTH);
		keyParser.parsePublicKey(b);
		if (LOG.isLoggable(INFO)) LOG.info(pluginName + " received key");
		return b;
	}

	SecretKey deriveMasterSecret(byte[] hash, byte[] key, boolean alice)
			throws GeneralSecurityException {
		// Check that the hash matches the key
		byte[] keyHash =
				crypto.hash(LABEL_PUBLIC_KEY, keyPair.getPublic().getEncoded());
		if (!Arrays.equals(hash, keyHash)) {
			if (LOG.isLoggable(INFO))
				LOG.info(pluginName + " hash does not match key");
			throw new GeneralSecurityException();
		}
		//  Derive the master secret
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " deriving master secret");
		return crypto.deriveMasterSecret(key, keyPair, alice);
	}

	void sendConfirmation(BdfWriter w, boolean confirmed) throws IOException {
		w.writeBoolean(confirmed);
		w.flush();
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " sent confirmation: " + confirmed);
	}

	boolean receiveConfirmation(BdfReader r) throws IOException {
		boolean confirmed = r.readBoolean();
		if (LOG.isLoggable(INFO))
			LOG.info(pluginName + " received confirmation: " + confirmed);
		return confirmed;
	}

	protected void tryToClose(DuplexTransportConnection conn,
			boolean exception) {
		try {
			LOG.info("Closing connection");
			conn.getReader().dispose(exception, true);
			conn.getWriter().dispose(exception);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}
}
