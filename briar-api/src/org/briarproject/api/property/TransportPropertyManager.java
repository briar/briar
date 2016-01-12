package org.briarproject.api.property;

import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;

import java.util.Map;

public interface TransportPropertyManager {

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

	/**
	 * Sets the remote transport properties for the given contact, replacing
	 * any existing properties.
	 */
	void setRemoteProperties(ContactId c,
			Map<TransportId, TransportProperties> p) throws DbException;
}
