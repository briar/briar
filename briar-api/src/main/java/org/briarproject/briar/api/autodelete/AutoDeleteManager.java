package org.briarproject.briar.api.autodelete;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface AutoDeleteManager {

	long getAutoDeleteTimer(Transaction txn, ContactId c) throws DbException;

	void setAutoDeleteTimer(Transaction txn, ContactId c) throws DbException;
}
