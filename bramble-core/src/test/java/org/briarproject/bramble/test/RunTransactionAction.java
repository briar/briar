package org.briarproject.bramble.test;

import org.briarproject.bramble.api.db.DbRunnable;
import org.briarproject.bramble.api.db.Transaction;
import org.hamcrest.Description;
import org.jmock.api.Action;
import org.jmock.api.Invocation;

public class RunTransactionAction implements Action {

	private final Transaction txn;

	@SuppressWarnings("WeakerAccess")
	public RunTransactionAction(Transaction txn) {
		this.txn = txn;
	}

	@Override
	public Object invoke(Invocation invocation) throws Throwable {
		DbRunnable task = (DbRunnable) invocation.getParameter(1);
		task.run(txn);
		return null;
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("runs a task inside a database transaction");
	}

}
