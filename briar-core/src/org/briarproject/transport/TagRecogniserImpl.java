package org.briarproject.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
	private final Lock lock = new ReentrantLock();

	// Locking: lock
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
		lock.lock();
		try {
			r = recognisers.get(t);
		} finally {
			lock.unlock();
		}
		if (r == null) return null;
		return r.recogniseTag(tag);
	}

	public void addSecret(TemporarySecret s) {
		TransportId t = s.getTransportId();
		TransportTagRecogniser r;
		lock.lock();
		try {
			r = recognisers.get(t);
			if (r == null) {
				r = new TransportTagRecogniser(crypto, db, t);
				recognisers.put(t, r);
			}
		} finally {
			lock.unlock();
		}
		r.addSecret(s);
	}

	public void removeSecret(ContactId c, TransportId t, long period) {
		TransportTagRecogniser r;
		lock.lock();
		try {
			r = recognisers.get(t);
		} finally {
			lock.unlock();
		}
		if (r != null) r.removeSecret(c, period);
	}

	public void removeSecrets(ContactId c) {
		lock.lock();
		try {
			for (TransportTagRecogniser r : recognisers.values())
				r.removeSecrets(c);
		} finally {
			lock.unlock();
		}
	}

	public void removeSecrets(TransportId t) {
		lock.lock();
		try {
			recognisers.remove(t);
		} finally {
			lock.unlock();
		}

	}

	public void removeSecrets() {
		lock.lock();
		try {
			for (TransportTagRecogniser r : recognisers.values())
				r.removeSecrets();
		} finally {
			lock.unlock();
		}

	}
}
