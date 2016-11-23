package org.briarproject.bramble.api.properties;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.ClientId;

import java.util.Map;

@NotNullByDefault
public interface TransportPropertyManager {

	/**
	 * The unique ID of the transport property client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.properties");

	/**
	 * Stores the given properties received while adding a contact - they will
	 * be superseded by any properties synced from the contact.
	 */
	void addRemoteProperties(Transaction txn, ContactId c,
			Map<TransportId, TransportProperties> props) throws DbException;

	/**
	 * Returns the local transport properties for all transports.
	 */
	Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException;

	/**
	 * Returns the local transport properties for all transports.
	 * <br/>
	 * Read-Only
	 */
	Map<TransportId, TransportProperties> getLocalProperties(Transaction txn)
			throws DbException;

	/**
	 * Returns the local transport properties for the given transport.
	 */
	TransportProperties getLocalProperties(TransportId t) throws DbException;

	/**
	 * Returns all remote transport properties for the given transport.
	 */
	Map<ContactId, TransportProperties> getRemoteProperties(TransportId t)
			throws DbException;

	/**
	 * Merges the given properties with the existing local properties for the
	 * given transport.
	 */
	void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException;
}
