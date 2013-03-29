package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.TemporarySecret;
import net.sf.briar.util.ByteUtils;

/** A connection recogniser for a specific transport. */
class TransportConnectionRecogniser {

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final TransportId transportId;
	private final Map<Bytes, TagContext> tagMap; // Locking: this
	private final Map<RemovalKey, RemovalContext> removalMap; // Locking: this

	TransportConnectionRecogniser(CryptoComponent crypto, DatabaseComponent db,
			TransportId transportId) {
		this.crypto = crypto;
		this.db = db;
		this.transportId = transportId;
		tagMap = new HashMap<Bytes, TagContext>();
		removalMap = new HashMap<RemovalKey, RemovalContext>();
	}

	synchronized ConnectionContext acceptConnection(byte[] tag)
			throws DbException {
		TagContext tctx = tagMap.remove(new Bytes(tag));
		if(tctx == null) return null; // The tag was not expected
		ConnectionWindow window = tctx.window;
		ConnectionContext ctx = tctx.context;
		long period = tctx.period;
		ContactId contactId = ctx.getContactId();
		byte[] secret = ctx.getSecret();
		long connection = ctx.getConnectionNumber();
		boolean alice = ctx.getAlice();
		// Update the connection window and the expected tags
		Cipher cipher = crypto.getTagCipher();
		ErasableKey key = crypto.deriveTagKey(secret, !alice);
		for(long connection1 : window.setSeen(connection)) {
			byte[] tag1 = new byte[TAG_LENGTH];
			crypto.encodeTag(tag1, cipher, key, connection1);
			if(connection1 < connection) {
				TagContext old = tagMap.remove(new Bytes(tag1));
				assert old != null;
				ByteUtils.erase(old.context.getSecret());
			} else {
				ConnectionContext ctx1 = new ConnectionContext(contactId,
						transportId, secret.clone(), connection1, alice);
				TagContext tctx1 = new TagContext(window, ctx1, period);
				TagContext old = tagMap.put(new Bytes(tag1), tctx1);
				assert old == null;
			}
		}
		key.erase();
		// Store the updated connection window in the DB
		long centre = window.getCentre();
		byte[] bitmap = window.getBitmap();
		db.setConnectionWindow(contactId, transportId, period, centre, bitmap);
		return ctx;
	}

	synchronized void addSecret(TemporarySecret s) {
		ContactId contactId = s.getContactId();
		long period = s.getPeriod();
		byte[] secret = s.getSecret();
		boolean alice = s.getAlice();
		long centre = s.getWindowCentre();
		byte[] bitmap = s.getWindowBitmap();
		// Create the connection window and the expected tags
		Cipher cipher = crypto.getTagCipher();
		ErasableKey key = crypto.deriveTagKey(secret, !alice);
		ConnectionWindow window = new ConnectionWindow(centre, bitmap);
		for(long connection : window.getUnseen()) {
			byte[] tag = new byte[TAG_LENGTH];
			crypto.encodeTag(tag, cipher, key, connection);
			ConnectionContext ctx = new ConnectionContext(contactId,
					transportId, secret.clone(), connection, alice);
			TagContext tctx = new TagContext(window, ctx, period);
			TagContext old = tagMap.put(new Bytes(tag), tctx);
			assert old == null;
		}
		key.erase();
		// Create a removal context to remove the window later
		RemovalContext rctx = new RemovalContext(window, secret, alice);
		removalMap.put(new RemovalKey(contactId, period), rctx);
	}

	synchronized void removeSecret(ContactId contactId, long period) {
		RemovalKey rk = new RemovalKey(contactId, period);
		RemovalContext rctx = removalMap.remove(rk);
		if(rctx == null) throw new IllegalArgumentException();
		removeSecret(rctx);
	}

	// Locking: this
	private void removeSecret(RemovalContext rctx) {
		// Remove the expected tags
		Cipher cipher = crypto.getTagCipher();
		ErasableKey key = crypto.deriveTagKey(rctx.secret, !rctx.alice);
		byte[] tag = new byte[TAG_LENGTH];
		for(long connection : rctx.window.getUnseen()) {
			crypto.encodeTag(tag, cipher, key, connection);
			TagContext old = tagMap.remove(new Bytes(tag));
			assert old != null;
			ByteUtils.erase(old.context.getSecret());
		}
		key.erase();
		ByteUtils.erase(rctx.secret);
	}

	synchronized void removeSecrets(ContactId c) {
		Collection<RemovalKey> keysToRemove = new ArrayList<RemovalKey>();
		for(RemovalKey k : removalMap.keySet()) {
			if(k.contactId.equals(c)) keysToRemove.add(k);
		}
		for(RemovalKey k : keysToRemove) removeSecret(k.contactId, k.period);
	}

	synchronized void removeSecrets() {
		for(RemovalContext rctx : removalMap.values()) removeSecret(rctx);
		assert tagMap.isEmpty();
		removalMap.clear();
	}

	private static class TagContext {

		private final ConnectionWindow window;
		private final ConnectionContext context;
		private final long period;

		private TagContext(ConnectionWindow window, ConnectionContext context,
				long period) {
			this.window = window;
			this.context = context;
			this.period = period;
		}
	}

	private static class RemovalKey {

		private final ContactId contactId;
		private final long period;

		private RemovalKey(ContactId contactId, long period) {
			this.contactId = contactId;
			this.period = period;
		}

		@Override
		public int hashCode() {
			return contactId.hashCode() ^ (int) (period ^ (period >>> 32));
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof RemovalKey) {
				RemovalKey w = (RemovalKey) o;
				return contactId.equals(w.contactId) && period == w.period;
			}
			return false;
		}
	}

	private static class RemovalContext {

		private final ConnectionWindow window;
		private final byte[] secret;
		private final boolean alice;

		private RemovalContext(ConnectionWindow window, byte[] secret,
				boolean alice) {
			this.window = window;
			this.secret = secret;
			this.alice = alice;
		}
	}
}
