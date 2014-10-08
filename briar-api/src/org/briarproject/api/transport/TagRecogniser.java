package org.briarproject.api.transport;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DbException;

/** Maintains the table of expected tags for recognising incoming streams. */
public interface TagRecogniser {

	/**
	 * Returns a {@link StreamContext} for reading from the stream with the
	 * given tag if the tag was expected, or null if the tag was unexpected.
	 */
	StreamContext recogniseTag(TransportId t, byte[] tag) throws DbException;

	void addSecret(TemporarySecret s);

	void removeSecret(ContactId c, TransportId t, long period);

	void removeSecrets(ContactId c);

	void removeSecrets(TransportId t);

	void removeSecrets();
}
