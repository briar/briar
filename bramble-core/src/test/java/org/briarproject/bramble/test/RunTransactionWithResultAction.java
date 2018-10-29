package org.briarproject.bramble.test;

import org.briarproject.bramble.api.db.DbCallable;
import org.briarproject.bramble.api.db.Transaction;
import org.hamcrest.Description;
import org.jmock.api.Action;
import org.jmock.api.Invocation;

public class RunTransactionWithResultAction implements Action {

	private final Transaction txn;

	public RunTransactionWithResultAction(Transaction txn) {
		this.txn = txn;
	}

	@Override
	public Object invoke(Invocation invocation) throws Throwable {
		DbCallable task = (DbCallable) invocation.getParameter(1);
		return task.call(txn);
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("runs a task inside a database transaction");
	}
}
