package org.briarproject.bramble.test;

import org.hamcrest.Description;
import org.jmock.api.Action;
import org.jmock.api.Invocation;

public class RunAction implements Action {

	@Override
	public Object invoke(Invocation invocation) throws Throwable {
		Runnable task = (Runnable) invocation.getParameter(0);
		task.run();
		return null;
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("runs a runnable");
	}
}
