package org.briarproject.api.properties;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;

import java.util.Map;

public interface TransportPropertyManager {

	/**
	 * Stores the given properties received while adding a contact - they will
	 * be superseded by any properties synced from the contact.
	 */
	void addRemoteProperties(Transaction txn, ContactId c,
			Map<TransportId, TransportProperties> props) throws DbException;

	/** Returns the local transport properties for all transports. */
	Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException;

	/** Returns the local transport properties for the given transport. */
	TransportProperties getLocalProperties(TransportId t) throws DbException;

	/** Returns all remote transport properties for the given transport. */
	Map<ContactId, TransportProperties> getRemoteProperties(TransportId t)
			throws DbException;

	/**
	 * Merges the given properties with the existing local properties for the
	 * given transport.
	 */
	void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException;
}
