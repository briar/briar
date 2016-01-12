package org.briarproject.property;

import com.google.inject.Inject;

import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.property.TransportPropertyManager;

import java.util.Map;

// Temporary facade during sync protocol refactoring
class TransportPropertyManagerImpl implements TransportPropertyManager {

	private final DatabaseComponent db;

	@Inject
	TransportPropertyManagerImpl(DatabaseComponent db) {
		this.db = db;
	}

	@Override
	public Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException {
		return db.getLocalProperties();
	}

	@Override
	public TransportProperties getLocalProperties(TransportId t)
			throws DbException {
		return db.getLocalProperties(t);
	}

	@Override
	public Map<ContactId, TransportProperties> getRemoteProperties(
			TransportId t) throws DbException {
		return db.getRemoteProperties(t);
	}

	@Override
	public void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException {
		db.mergeLocalProperties(t, p);
	}

	@Override
	public void setRemoteProperties(ContactId c,
			Map<TransportId, TransportProperties> p) throws DbException {
		db.setRemoteProperties(c, p);
	}
}
