package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
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

	KeySetId addContact(Transaction txn, ContactId c, SecretKey master,
			long timestamp, boolean alice, boolean active) throws DbException;

	void activateKeys(Transaction txn, KeySetId k) throws DbException;

	void removeContact(ContactId c);

	boolean canSendOutgoingStreams(ContactId c);

	@Nullable
	StreamContext getStreamContext(Transaction txn, ContactId c)
			throws DbException;

	@Nullable
	StreamContext getStreamContext(Transaction txn, byte[] tag)
			throws DbException;

}
