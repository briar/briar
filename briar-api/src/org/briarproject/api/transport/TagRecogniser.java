package org.briarproject.api.transport;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DbException;

/** Keeps track of expected tags and uses them to recognise incoming streams. */
public interface TagRecogniser {

	/**
	 * Looks up the given tag and returns a {@link StreamContext} for reading
	 * from the stream if the tag was expected, or null if the tag was
	 * unexpected.
	 */
	StreamContext recogniseTag(TransportId t, byte[] tag) throws DbException;

	void addSecret(TemporarySecret s);

	void removeSecret(ContactId c, TransportId t, long period);

	void removeSecrets(ContactId c);

	void removeSecrets(TransportId t);

	void removeSecrets();
}
