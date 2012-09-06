package net.sf.briar.plugins;

import static net.sf.briar.api.plugins.InvitationConstants.HASH_LENGTH;
import static net.sf.briar.api.plugins.InvitationConstants.INVITATION_TIMEOUT;
import static net.sf.briar.api.plugins.InvitationConstants.MAX_CODE;
import static net.sf.briar.api.plugins.InvitationConstants.MAX_PUBLIC_KEY_LENGTH;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.concurrent.Executor;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.IncomingInvitationCallback;
import net.sf.briar.api.plugins.InvitationCallback;
import net.sf.briar.api.plugins.InvitationStarter;
import net.sf.briar.api.plugins.OutgoingInvitationCallback;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Inject;

class InvitationStarterImpl implements InvitationStarter {

	private static final String TIMED_OUT = "INVITATION_TIMED_OUT";
	private static final String IO_EXCEPTION = "INVITATION_IO_EXCEPTION";
	private static final String INVALID_KEY = "INVITATION_INVALID_KEY";
	private static final String WRONG_CODE = "INVITATION_WRONG_CODE";
	private static final String DB_EXCEPTION = "INVITATION_DB_EXCEPTION";

	private final Executor pluginExecutor;
	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;

	@Inject
	InvitationStarterImpl(@PluginExecutor Executor pluginExecutor,
			CryptoComponent crypto, DatabaseComponent db,
			ReaderFactory readerFactory, WriterFactory writerFactory) {
		this.pluginExecutor = pluginExecutor;
		this.crypto = crypto;
		this.db = db;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
	}

	public void startIncomingInvitation(DuplexPlugin plugin,
			IncomingInvitationCallback callback) {
		pluginExecutor.execute(new IncomingInvitationWorker(plugin, callback));
	}

	public void startOutgoingInvitation(DuplexPlugin plugin,
			OutgoingInvitationCallback callback) {
		pluginExecutor.execute(new OutgoingInvitationWorker(plugin, callback));
	}

	private abstract class InvitationWorker implements Runnable {

		private final DuplexPlugin plugin;
		private final InvitationCallback callback;
		private final boolean initiator;

		protected InvitationWorker(DuplexPlugin plugin,
				InvitationCallback callback, boolean initiator) {
			this.plugin = plugin;
			this.callback = callback;
			this.initiator = initiator;
		}

		protected abstract int getInvitationCode();

		public void run() {
			long end = System.currentTimeMillis() + INVITATION_TIMEOUT;
			// Use the invitation code to seed the PRNG
			int code = getInvitationCode();
			if(code == -1) return; // Cancelled
			PseudoRandom r = crypto.getPseudoRandom(code);
			long timeout = end - System.currentTimeMillis();
			if(timeout <= 0) {
				callback.showFailure(TIMED_OUT);
				return;
			}
			// Create a connection
			DuplexTransportConnection conn;
			if(initiator) conn = plugin.sendInvitation(r, timeout);
			else conn = plugin.acceptInvitation(r, timeout);
			if(callback.isCancelled()) {
				if(conn != null) conn.dispose(false, false);
				return;
			}
			if(conn == null) {
				callback.showFailure(TIMED_OUT);
				return;
			}
			// Use an ephemeral key pair for key agreement
			KeyPair ourKeyPair = crypto.generateAgreementKeyPair();
			MessageDigest messageDigest = crypto.getMessageDigest();
			byte[] ourKey = ourKeyPair.getPublic().getEncoded();
			byte[] ourHash = messageDigest.digest(ourKey);
			byte[] theirKey, theirHash;
			try {
				OutputStream out = conn.getOutputStream();
				Writer writer = writerFactory.createWriter(out);
				InputStream in = conn.getInputStream();
				Reader reader = readerFactory.createReader(in);
				if(initiator) {
					// Send the public key hash
					writer.writeBytes(ourHash);
					out.flush();
					// Receive the public key hash
					theirHash = reader.readBytes(HASH_LENGTH);
					// Send the public key
					writer.writeBytes(ourKey);
					out.flush();
					// Receive the public key
					theirKey = reader.readBytes(MAX_PUBLIC_KEY_LENGTH);
				} else {
					// Receive the public key hash
					theirHash = reader.readBytes(HASH_LENGTH);
					// Send the public key hash
					writer.writeBytes(ourHash);
					out.flush();
					// Receive the public key
					theirKey = reader.readBytes(MAX_PUBLIC_KEY_LENGTH);
					// Send the public key
					writer.writeBytes(ourKey);
					out.flush();
				}
			} catch(IOException e) {
				conn.dispose(true, false);
				callback.showFailure(IO_EXCEPTION);
				return;
			}
			conn.dispose(false, false);
			if(callback.isCancelled()) return;
			// Check that the received hash matches the received key
			if(!Arrays.equals(theirHash, messageDigest.digest(theirKey))) {
				callback.showFailure(INVALID_KEY);
				return;
			}
			// Derive the initial shared secrets and the confirmation codes
			byte[][] secrets = crypto.deriveInitialSecrets(ourKey, theirKey,
					ourKeyPair.getPrivate(), code, initiator);
			if(secrets == null) {
				callback.showFailure(INVALID_KEY);
				return;
			}
			int initCode = crypto.deriveConfirmationCode(secrets[0]);
			int respCode = crypto.deriveConfirmationCode(secrets[1]);
			int ourCode = initiator ? initCode : respCode;
			int theirCode = initiator ? respCode : initCode;
			// Compare the confirmation codes
			if(callback.enterConfirmationCode(ourCode) != theirCode) {
				callback.showFailure(WRONG_CODE);
				ByteUtils.erase(secrets[0]);
				ByteUtils.erase(secrets[1]);
				return;
			}
			// Add the contact to the database
			byte[] inSecret = initiator ? secrets[1] : secrets[0];
			byte[] outSecret = initiator ? secrets[0] : secrets[1];
			try {
				db.addContact(inSecret, outSecret);
			} catch(DbException e) {
				callback.showFailure(DB_EXCEPTION);
				ByteUtils.erase(secrets[0]);
				ByteUtils.erase(secrets[1]);
				return;
			}
			callback.showSuccess();
		}
	}

	private class IncomingInvitationWorker extends InvitationWorker {

		private final IncomingInvitationCallback callback;

		IncomingInvitationWorker(DuplexPlugin plugin,
				IncomingInvitationCallback callback) {
			super(plugin, callback, false);
			this.callback = callback;
		}

		@Override
		protected int getInvitationCode() {
			return callback.enterInvitationCode();
		}
	}

	private class OutgoingInvitationWorker extends InvitationWorker {

		private final OutgoingInvitationCallback callback;

		OutgoingInvitationWorker(DuplexPlugin plugin,
				OutgoingInvitationCallback callback) {
			super(plugin, callback, true);
			this.callback = callback;
		}

		@Override
		protected int getInvitationCode() {
			int code = crypto.getSecureRandom().nextInt(MAX_CODE + 1);
			callback.showInvitationCode(code);
			return code;
		}
	}
}
