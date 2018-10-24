package org.briarproject.bramble.test;

import org.briarproject.bramble.api.db.DbRunnable;
import org.briarproject.bramble.api.db.Transaction;
import org.jmock.Expectations;

public class DbExpectations extends Expectations {

	protected <E extends Exception> DbRunnable<E> withDbRunnable(
			Transaction txn) {
		addParameterMatcher(any(DbRunnable.class));
		currentBuilder().setAction(new RunTransactionAction(txn));
		return null;
	}

}
