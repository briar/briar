package net.sf.briar.invitation;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.plugins.InvitationConstants.HASH_LENGTH;
import static net.sf.briar.api.plugins.InvitationConstants.INVITATION_TIMEOUT;
import static net.sf.briar.api.plugins.InvitationConstants.MAX_PUBLIC_KEY_LENGTH;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.invitation.ConfirmationCallback;
import net.sf.briar.api.invitation.ConnectionCallback;
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
	protected final DuplexPlugin plugin;
	protected final PseudoRandom random;
	protected final ConnectionCallback callback;
	protected final AtomicBoolean connected, succeeded;
	protected final String pluginName;

	private final KeyPair keyPair;
	private final KeyParser keyParser;
	private final MessageDigest messageDigest;

	Connector(CryptoComponent crypto, ReaderFactory readerFactory,
			WriterFactory writerFactory, DuplexPlugin plugin,
			PseudoRandom random, ConnectionCallback callback,
			AtomicBoolean connected, AtomicBoolean succeeded) {
		this.crypto = crypto;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.plugin = plugin;
		this.random = random;
		this.callback = callback;
		this.connected = connected;
		this.succeeded = succeeded;
		pluginName = plugin.getClass().getName();
		keyPair = crypto.generateAgreementKeyPair();
		keyParser = crypto.getAgreementKeyParser();
		messageDigest = crypto.getMessageDigest();
	}

	protected DuplexTransportConnection acceptIncomingConnection() {
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " accepting incoming connection");
		return plugin.acceptInvitation(random, INVITATION_TIMEOUT / 2);
	}

	protected DuplexTransportConnection makeOutgoingConnection() {
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " making outgoing connection");
		return plugin.sendInvitation(random, INVITATION_TIMEOUT / 2);
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
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " received hash");
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

	protected static class ConfirmationSender implements ConfirmationCallback {

		private final Writer writer;

		protected ConfirmationSender(Writer writer) {
			this.writer = writer;
		}

		public void codesMatch() {
			write(true);
		}

		public void codesDoNotMatch() {
			write(false);
		}

		private void write(boolean match) {
			try {
				writer.writeBoolean(match);
				writer.flush();
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			}
		}
	}
}
