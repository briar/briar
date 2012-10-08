package net.sf.briar.transport;

import java.util.HashMap;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionRecogniser;

import com.google.inject.Inject;

class ConnectionRecogniserImpl implements ConnectionRecogniser {

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	// Locking: this
	private final Map<TransportId, TransportConnectionRecogniser> recognisers;

	@Inject
	ConnectionRecogniserImpl(CryptoComponent crypto, DatabaseComponent db) {
		this.crypto = crypto;
		this.db = db;
		recognisers = new HashMap<TransportId, TransportConnectionRecogniser>();
	}

	public ConnectionContext acceptConnection(TransportId t, byte[] tag)
			throws DbException {
		TransportConnectionRecogniser r;
		synchronized(this) {
			r = recognisers.get(t);
		}
		if(r == null) return null;
		return r.acceptConnection(tag);
	}

	public void addWindow(ContactId c, TransportId t, long period,
			boolean alice, byte[] secret, long centre, byte[] bitmap)
					throws DbException {
		TransportConnectionRecogniser r;
		synchronized(this) {
			r = recognisers.get(t);
			if(r == null) {
				r = new TransportConnectionRecogniser(crypto, db, t);
				recognisers.put(t, r);
			}
		}
		r.addWindow(c, period, alice, secret, centre, bitmap);
	}

	public void removeWindow(ContactId c, TransportId t, long period) {
		TransportConnectionRecogniser r;
		synchronized(this) {
			r = recognisers.get(t);
		}
		if(r != null) r.removeWindow(c, period);
	}

	public synchronized void removeWindows(ContactId c) {
		for(TransportConnectionRecogniser r : recognisers.values()) {
			r.removeWindows(c);
		}
	}
}
