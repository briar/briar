package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.TransportKeySetId;

import javax.annotation.Nullable;

@NotNullByDefault
interface TransportKeyManager {

	void start(Transaction txn) throws DbException;

	TransportKeySetId addContact(Transaction txn, ContactId c,
			SecretKey rootKey, long timestamp, boolean alice, boolean active)
			throws DbException;

	void activateKeys(Transaction txn, TransportKeySetId k) throws DbException;

	void removeContact(ContactId c);

	boolean canSendOutgoingStreams(ContactId c);

	@Nullable
	StreamContext getStreamContext(Transaction txn, ContactId c)
			throws DbException;

	@Nullable
	StreamContext getStreamContext(Transaction txn, byte[] tag)
			throws DbException;

}
