package org.briarproject.bramble.test;

import org.briarproject.bramble.api.db.NullableDbCallable;
import org.briarproject.bramble.api.db.Transaction;
import org.hamcrest.Description;
import org.jmock.api.Action;
import org.jmock.api.Invocation;

public class RunTransactionWithNullableResultAction implements Action {

	private final Transaction txn;

	public RunTransactionWithNullableResultAction(Transaction txn) {
		this.txn = txn;
	}

	@Override
	public Object invoke(Invocation invocation) throws Throwable {
		NullableDbCallable task =
				(NullableDbCallable) invocation.getParameter(1);
		return task.call(txn);
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("runs a task inside a database transaction");
	}
}
