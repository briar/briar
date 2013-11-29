package net.sf.briar.transport;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.TemporarySecret;

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

	public void addSecret(TemporarySecret s) {
		TransportId t = s.getTransportId();
		TransportConnectionRecogniser r;
		synchronized(this) {
			r = recognisers.get(t);
			if(r == null) {
				r = new TransportConnectionRecogniser(crypto, db, t);
				recognisers.put(t, r);
			}
		}
		r.addSecret(s);
	}

	public void removeSecret(ContactId c, TransportId t, long period) {
		TransportConnectionRecogniser r;
		synchronized(this) {
			r = recognisers.get(t);
		}
		if(r != null) r.removeSecret(c, period);
	}

	public synchronized void removeSecrets(ContactId c) {
		for(TransportConnectionRecogniser r : recognisers.values())
			r.removeSecrets(c);
	}

	public synchronized void removeSecrets(TransportId t) {
		recognisers.remove(t);
	}

	public synchronized void removeSecrets() {
		for(TransportConnectionRecogniser r : recognisers.values())
			r.removeSecrets();
	}
}
