package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.StreamContext;

import javax.annotation.Nullable;

@NotNullByDefault
interface TransportKeyManager {

	void start(Transaction txn) throws DbException;

	KeySetId addRotationKeys(Transaction txn, ContactId c,
			SecretKey rootKey, long timestamp, boolean alice, boolean active)
			throws DbException;

	KeySetId addHandshakeKeys(Transaction txn, ContactId c,
			SecretKey rootKey, boolean alice) throws DbException;

	KeySetId addHandshakeKeys(Transaction txn, PendingContactId p,
			SecretKey rootKey, boolean alice) throws DbException;

	void activateKeys(Transaction txn, KeySetId k) throws DbException;

	void removeContact(ContactId c);

	void removePendingContact(PendingContactId p);

	boolean canSendOutgoingStreams(ContactId c);

	boolean canSendOutgoingStreams(PendingContactId p);

	@Nullable
	StreamContext getStreamContext(Transaction txn, ContactId c)
			throws DbException;

	@Nullable
	StreamContext getStreamContext(Transaction txn, PendingContactId p)
			throws DbException;

	@Nullable
	StreamContext getStreamContext(Transaction txn, byte[] tag)
			throws DbException;

	@Nullable
	StreamContext getStreamContextOnly(Transaction txn, byte[] tag);

	void markTagAsRecognised(Transaction txn, byte[] tag) throws DbException;

}
