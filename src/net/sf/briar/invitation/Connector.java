package net.sf.briar.invitation;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.plugins.InvitationConstants.HASH_LENGTH;
import static net.sf.briar.api.plugins.InvitationConstants.CONNECTION_TIMEOUT;
import static net.sf.briar.api.plugins.InvitationConstants.MAX_PUBLIC_KEY_LENGTH;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.logging.Logger;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

abstract class Connector extends Thread {

	private static final Logger LOG =
			Logger.getLogger(Connector.class.getName());

	protected final CryptoComponent crypto;
	protected final ReaderFactory readerFactory;
	protected final WriterFactory writerFactory;
	protected final ConnectorGroup group;
	protected final DuplexPlugin plugin;
	protected final PseudoRandom random;
	protected final String pluginName;

	private final KeyPair keyPair;
	private final KeyParser keyParser;
	private final MessageDigest messageDigest;

	Connector(CryptoComponent crypto, ReaderFactory readerFactory,
			WriterFactory writerFactory, ConnectorGroup group,
			DuplexPlugin plugin, PseudoRandom random) {
		this.crypto = crypto;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.group = group;
		this.plugin = plugin;
		this.random = random;
		pluginName = plugin.getClass().getName();
		keyPair = crypto.generateAgreementKeyPair();
		keyParser = crypto.getAgreementKeyParser();
		messageDigest = crypto.getMessageDigest();
	}

	protected DuplexTransportConnection acceptIncomingConnection() {
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " accepting incoming connection");
		return plugin.acceptInvitation(random, CONNECTION_TIMEOUT);
	}

	protected DuplexTransportConnection makeOutgoingConnection() {
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " making outgoing connection");
		return plugin.sendInvitation(random, CONNECTION_TIMEOUT);
	}

	protected void waitForHalfTime(long halfTime) {
		long now = System.currentTimeMillis();
		if(now < halfTime) {
			if(LOG.isLoggable(INFO))
				LOG.info(pluginName + " sleeping until half-time");
			try {
				Thread.sleep(halfTime - now);
			} catch(InterruptedException e) {
				if(LOG.isLoggable(INFO)) LOG.info("Interrupted while sleeping");
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	protected void tryToClose(DuplexTransportConnection conn,
			boolean exception) {
		try {
			conn.dispose(exception, true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}

	protected void sendPublicKeyHash(Writer w) throws IOException {
		w.writeBytes(messageDigest.digest(keyPair.getPublic().getEncoded()));
		w.flush();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " sent hash");
	}

	protected byte[] receivePublicKeyHash(Reader r) throws IOException {
		byte[] b = r.readBytes(HASH_LENGTH);
		if(b.length != HASH_LENGTH) throw new FormatException();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " received hash");
		return b;
	}

	protected void sendPublicKey(Writer w) throws IOException {
		w.writeBytes(keyPair.getPublic().getEncoded());
		w.flush();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " sent key");
	}

	protected byte[] receivePublicKey(Reader r) throws IOException {
		byte[] b = r.readBytes(MAX_PUBLIC_KEY_LENGTH);
		try {
			keyParser.parsePublicKey(b);
		} catch(GeneralSecurityException e) {
			throw new FormatException();
		}
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " received key");
		return b;
	}

	protected byte[] deriveSharedSecret(byte[] hash, byte[] key, boolean alice)
			throws GeneralSecurityException {
		// Check that the hash matches the key
		if(!Arrays.equals(hash, messageDigest.digest(key))) {
			if(LOG.isLoggable(INFO))
				LOG.info(pluginName + " hash does not match key");
			throw new GeneralSecurityException();
		}
		//  Derive the shared secret
		return crypto.deriveInitialSecret(key, keyPair, alice);
	}

	protected void sendConfirmation(Writer w) throws IOException,
	InterruptedException {
		boolean matched = group.waitForLocalConfirmationResult();
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " sent confirmation");
		w.writeBoolean(matched);
		w.flush();
	}

	protected boolean receiveConfirmation(Reader r) throws IOException {
		boolean matched = r.readBoolean();
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " received confirmation");
		return matched;
	}
}
