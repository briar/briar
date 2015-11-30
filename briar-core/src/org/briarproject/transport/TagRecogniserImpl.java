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
	private final Lock synchLock = new ReentrantLock();

	// Locking: synchLock
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
		synchLock.lock();
		try {
			r = recognisers.get(t);
		} finally {
			synchLock.unlock();
		}
		if (r == null) return null;
		return r.recogniseTag(tag);
	}

	public void addSecret(TemporarySecret s) {
		TransportId t = s.getTransportId();
		TransportTagRecogniser r;
		synchLock.lock();
		try {
			r = recognisers.get(t);
			if (r == null) {
				r = new TransportTagRecogniser(crypto, db, t);
				recognisers.put(t, r);
			}
		} finally {
			synchLock.unlock();
		}
		r.addSecret(s);
	}

	public void removeSecret(ContactId c, TransportId t, long period) {
		TransportTagRecogniser r;
		synchLock.lock();
		try {
			r = recognisers.get(t);
		} finally {
			synchLock.unlock();
		}
		if (r != null) r.removeSecret(c, period);
	}

	public void removeSecrets(ContactId c) {
		synchLock.lock();
		try {
			for (TransportTagRecogniser r : recognisers.values())
				r.removeSecrets(c);
		} finally {
			synchLock.unlock();
		}
	}

	public void removeSecrets(TransportId t) {
		synchLock.lock();
		try {
			recognisers.remove(t);
		} finally {
			synchLock.unlock();
		}

	}

	public void removeSecrets() {
		synchLock.lock();
		try {
			for (TransportTagRecogniser r : recognisers.values())
				r.removeSecrets();
		} finally {
			synchLock.unlock();
		}

	}
}
