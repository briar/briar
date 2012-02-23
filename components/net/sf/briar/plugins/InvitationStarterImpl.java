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

import javax.inject.Inject;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.IncomingInvitationCallback;
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

// FIXME: Refactor this class to remove duplicated code
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

	public void startIncomingInvitation(final DuplexPlugin plugin,
			final IncomingInvitationCallback callback) {
		pluginExecutor.execute(new Runnable() {
			public void run() {
				long end = System.currentTimeMillis() + INVITATION_TIMEOUT;
				// Get the invitation code from the inviter
				int code = callback.enterInvitationCode();
				if(code == -1) return;
				long remaining = end - System.currentTimeMillis();
				if(remaining <= 0) return;
				// Use the invitation code to seed the PRNG
				PseudoRandom r = crypto.getPseudoRandom(code);
				// Connect to the inviter
				DuplexTransportConnection conn = plugin.acceptInvitation(r,
						remaining);
				if(callback.isCancelled()) {
					if(conn != null) conn.dispose(false, false);
					return;
				}
				if(conn == null) {
					callback.showFailure(TIMED_OUT);
					return;
				}
				KeyPair ourKeyPair = crypto.generateKeyPair();
				MessageDigest messageDigest = crypto.getMessageDigest();
				byte[] ourKey = ourKeyPair.getPublic().getEncoded();
				byte[] ourHash = messageDigest.digest(ourKey);
				byte[] theirKey, theirHash;
				try {
					// Send the public key hash
					OutputStream out = conn.getOutputStream();
					Writer writer = writerFactory.createWriter(out);
					writer.writeBytes(ourHash);
					out.flush();
					// Receive the public key hash
					InputStream in = conn.getInputStream();
					Reader reader = readerFactory.createReader(in);
					theirHash = reader.readBytes(HASH_LENGTH);
					// Send the public key
					writer.writeBytes(ourKey);
					out.flush();
					// Receive the public key
					theirKey = reader.readBytes(MAX_PUBLIC_KEY_LENGTH);
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
				byte[][] secrets = crypto.deriveInitialSecrets(theirKey,
						ourKeyPair, code, false);
				if(secrets == null) {
					callback.showFailure(INVALID_KEY);
					return;
				}
				int theirCode = crypto.deriveConfirmationCode(secrets[0], true);
				int ourCode = crypto.deriveConfirmationCode(secrets[1], false);
				// Compare the confirmation codes
				if(callback.enterConfirmationCode(ourCode) != theirCode) {
					callback.showFailure(WRONG_CODE);
					ByteUtils.erase(secrets[0]);
					ByteUtils.erase(secrets[1]);
					return;
				}
				// Add the contact to the database
				try {
					db.addContact(secrets[0], secrets[1]);
				} catch(DbException e) {
					callback.showFailure(DB_EXCEPTION);
					ByteUtils.erase(secrets[0]);
					ByteUtils.erase(secrets[1]);
					return;
				}
				callback.showSuccess();
			}
		});
	}

	public void startOutgoingInvitation(final DuplexPlugin plugin,
			final OutgoingInvitationCallback callback) {
		pluginExecutor.execute(new Runnable() {
			public void run() {
				// Generate an invitation code and use it to seed the PRNG
				int code = crypto.getSecureRandom().nextInt(MAX_CODE + 1);
				PseudoRandom r = crypto.getPseudoRandom(code);
				// Connect to the invitee
				DuplexTransportConnection conn = plugin.sendInvitation(r,
						INVITATION_TIMEOUT);
				if(callback.isCancelled()) {
					if(conn != null) conn.dispose(false, false);
					return;
				}
				if(conn == null) {
					callback.showFailure(TIMED_OUT);
					return;
				}
				KeyPair ourKeyPair = crypto.generateKeyPair();
				MessageDigest messageDigest = crypto.getMessageDigest();
				byte[] ourKey = ourKeyPair.getPublic().getEncoded();
				byte[] ourHash = messageDigest.digest(ourKey);
				byte[] theirKey, theirHash;
				try {
					// Receive the public key hash
					InputStream in = conn.getInputStream();
					Reader reader = readerFactory.createReader(in);
					theirHash = reader.readBytes(HASH_LENGTH);
					// Send the public key hash
					OutputStream out = conn.getOutputStream();
					Writer writer = writerFactory.createWriter(out);
					writer.writeBytes(ourHash);
					out.flush();
					// Receive the public key
					theirKey = reader.readBytes(MAX_PUBLIC_KEY_LENGTH);
					// Send the public key
					writer.writeBytes(ourKey);
					out.flush();
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
				// Derive the shared secret and the confirmation codes
				byte[][] secrets = crypto.deriveInitialSecrets(theirKey,
						ourKeyPair, code, true);
				if(secrets == null) {
					callback.showFailure(INVALID_KEY);
					return;
				}
				int ourCode = crypto.deriveConfirmationCode(secrets[0], true);
				int theirCode = crypto.deriveConfirmationCode(secrets[1],
						false);
				// Compare the confirmation codes
				if(callback.enterConfirmationCode(ourCode) != theirCode) {
					callback.showFailure(WRONG_CODE);
					ByteUtils.erase(secrets[0]);
					ByteUtils.erase(secrets[1]);
					return;
				}
				// Add the contact to the database
				try {
					db.addContact(secrets[1], secrets[0]);
				} catch(DbException e) {
					callback.showFailure(DB_EXCEPTION);
					ByteUtils.erase(secrets[0]);
					ByteUtils.erase(secrets[1]);
					return;
				}
				callback.showSuccess();
			}
		});
	}
}
