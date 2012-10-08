package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.util.ByteUtils;

/** A connection recogniser for a specific transport. */
class TransportConnectionRecogniser {

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final TransportId transportId;
	private final Map<Bytes, WindowContext> tagMap; // Locking: this
	private final Map<RemovalKey, RemovalContext> windowMap; // Locking: this

	TransportConnectionRecogniser(CryptoComponent crypto, DatabaseComponent db,
			TransportId transportId) {
		this.crypto = crypto;
		this.db = db;
		this.transportId = transportId;
		tagMap = new HashMap<Bytes, WindowContext>();
		windowMap = new HashMap<RemovalKey, RemovalContext>();
	}

	synchronized ConnectionContext acceptConnection(byte[] tag)
			throws DbException {
		WindowContext wctx = tagMap.remove(new Bytes(tag));
		if(wctx == null) return null;
		ConnectionWindow w = wctx.window;
		ConnectionContext ctx = wctx.context;
		long connection = ctx.getConnectionNumber();
		Cipher cipher = crypto.getTagCipher();
		ErasableKey key = crypto.deriveTagKey(ctx.getSecret(), ctx.getAlice());
		byte[] changedTag = new byte[TAG_LENGTH];
		Bytes changedTagWrapper = new Bytes(changedTag);
		for(long conn : w.setSeen(connection)) {
			TagEncoder.encodeTag(changedTag, cipher, key, conn);
			WindowContext old;
			if(conn <= connection) old = tagMap.remove(changedTagWrapper);
			else old = tagMap.put(changedTagWrapper, wctx);
			assert old == null;
		}
		key.erase();
		ContactId c = ctx.getContactId();
		long centre = w.getCentre();
		byte[] bitmap = w.getBitmap();
		db.setConnectionWindow(c, transportId, wctx.period, centre, bitmap);
		return wctx.context;
	}

	synchronized void addWindow(ContactId c, long period, boolean alice,
			byte[] secret, long centre, byte[] bitmap) throws DbException {
		Cipher cipher = crypto.getTagCipher();
		ErasableKey key = crypto.deriveTagKey(secret, alice);
		ConnectionWindow w = new ConnectionWindow(centre, bitmap);
		for(long conn : w.getUnseen()) {
			byte[] tag = new byte[TAG_LENGTH];
			TagEncoder.encodeTag(tag, cipher, key, conn);
			ConnectionContext ctx = new ConnectionContext(c, transportId, tag,
					secret, conn, alice);
			WindowContext wctx = new WindowContext(w, ctx, period);
			tagMap.put(new Bytes(tag), wctx);
		}
		db.setConnectionWindow(c, transportId, period, centre, bitmap);
		RemovalContext ctx = new RemovalContext(w, secret, alice);
		windowMap.put(new RemovalKey(c, period), ctx);
	}

	synchronized void removeWindow(ContactId c, long period) {
		RemovalContext ctx = windowMap.remove(new RemovalKey(c, period));
		if(ctx == null) throw new IllegalArgumentException();
		Cipher cipher = crypto.getTagCipher();
		ErasableKey key = crypto.deriveTagKey(ctx.secret, ctx.alice);
		byte[] removedTag = new byte[TAG_LENGTH];
		Bytes removedTagWrapper = new Bytes(removedTag);
		for(long conn : ctx.window.getUnseen()) {
			TagEncoder.encodeTag(removedTag, cipher, key, conn);
			WindowContext old = tagMap.remove(removedTagWrapper);
			assert old != null;
		}
		key.erase();
		ByteUtils.erase(ctx.secret);
	}

	synchronized void removeWindows(ContactId c) {
		Collection<RemovalKey> keysToRemove = new ArrayList<RemovalKey>();
		for(RemovalKey k : windowMap.keySet()) {
			if(k.contactId.equals(c)) keysToRemove.add(k);
		}
		for(RemovalKey k : keysToRemove) removeWindow(k.contactId, k.period);
	}

	private static class WindowContext {

		private final ConnectionWindow window;
		private final ConnectionContext context;
		private final long period;

		private WindowContext(ConnectionWindow window,
				ConnectionContext context, long period) {
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
			return contactId.hashCode()+ (int) period;
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
