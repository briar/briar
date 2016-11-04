package org.briarproject.transport;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.transport.StreamContext;

interface TransportKeyManager {

	void start(Transaction txn) throws DbException;

	void addContact(Transaction txn, ContactId c, SecretKey master,
			long timestamp, boolean alice) throws DbException;

	void removeContact(ContactId c);

	StreamContext getStreamContext(Transaction txn, ContactId c)
			throws DbException;

	StreamContext getStreamContext(Transaction txn, byte[] tag)
			throws DbException;

}
