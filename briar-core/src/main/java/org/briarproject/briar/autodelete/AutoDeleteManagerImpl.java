package org.briarproject.briar.autodelete;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.autodelete.AutoDeleteManager;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;

@Immutable
@NotNullByDefault
class AutoDeleteManagerImpl implements AutoDeleteManager {

	@Inject
	AutoDeleteManagerImpl() {
	}

	@Override
	public long getAutoDeleteTimer(Transaction txn, ContactId c)
			throws DbException {
		return NO_AUTO_DELETE_TIMER;
	}

	@Override
	public void setAutoDeleteTimer(Transaction txn, ContactId c)
			throws DbException {
		// Mmm hmm, yup, I'll bear that in mind
	}
}
