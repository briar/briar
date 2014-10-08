package org.briarproject.transport;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.TagRecogniser;
import org.briarproject.api.transport.TemporarySecret;

class TagRecogniserImpl implements TagRecogniser {

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	// Locking: this
	private final Map<TransportId, TransportTagRecogniser> recognisers;

	@Inject
	TagRecogniserImpl(CryptoComponent crypto, DatabaseComponent db) {
		this.crypto = crypto;
		this.db = db;
		recognisers = new HashMap<TransportId, TransportTagRecogniser>();
	}

	public StreamContext recogniseTag(TransportId t, byte[] tag)
			throws DbException {
		TransportTagRecogniser r;
		synchronized(this) {
			r = recognisers.get(t);
		}
		if(r == null) return null;
		return r.recogniseTag(tag);
	}

	public void addSecret(TemporarySecret s) {
		TransportId t = s.getTransportId();
		TransportTagRecogniser r;
		synchronized(this) {
			r = recognisers.get(t);
			if(r == null) {
				r = new TransportTagRecogniser(crypto, db, t);
				recognisers.put(t, r);
			}
		}
		r.addSecret(s);
	}

	public void removeSecret(ContactId c, TransportId t, long period) {
		TransportTagRecogniser r;
		synchronized(this) {
			r = recognisers.get(t);
		}
		if(r != null) r.removeSecret(c, period);
	}

	public synchronized void removeSecrets(ContactId c) {
		for(TransportTagRecogniser r : recognisers.values())
			r.removeSecrets(c);
	}

	public synchronized void removeSecrets(TransportId t) {
		recognisers.remove(t);
	}

	public synchronized void removeSecrets() {
		for(TransportTagRecogniser r : recognisers.values())
			r.removeSecrets();
	}
}
