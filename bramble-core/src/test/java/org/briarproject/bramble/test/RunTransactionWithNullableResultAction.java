package org.briarproject.bramble.test;

import org.briarproject.bramble.api.db.CommitAction;
import org.briarproject.bramble.api.db.NullableDbCallable;
import org.briarproject.bramble.api.db.TaskAction;
import org.briarproject.bramble.api.db.Transaction;
import org.hamcrest.Description;
import org.jmock.api.Action;
import org.jmock.api.Invocation;

class RunTransactionWithNullableResultAction implements Action {

	private final Transaction txn;

	RunTransactionWithNullableResultAction(Transaction txn) {
		this.txn = txn;
	}

	@Override
	public Object invoke(Invocation invocation) throws Throwable {
		NullableDbCallable task =
				(NullableDbCallable) invocation.getParameter(1);
		Object result = task.call(txn);
		for (CommitAction action : txn.getActions()) {
			if (action instanceof TaskAction)
				((TaskAction) action).getTask().run();
		}
		return result;
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("runs a task inside a database transaction");
	}
}
