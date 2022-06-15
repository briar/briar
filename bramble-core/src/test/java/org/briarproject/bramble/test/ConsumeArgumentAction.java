package org.briarproject.bramble.test;

import org.briarproject.bramble.api.Consumer;
import org.hamcrest.Description;
import org.jmock.api.Action;
import org.jmock.api.Invocation;

public class ConsumeArgumentAction<T> implements Action {

	private final Class<T> capturedClass;
	private final int index;
	private final Consumer<T> consumer;

	public ConsumeArgumentAction(Class<T> capturedClass, int index,
			Consumer<T> consumer) {
		this.capturedClass = capturedClass;
		this.index = index;
		this.consumer = consumer;
	}

	@Override
	public Object invoke(Invocation invocation) {
		consumer.accept(capturedClass.cast(invocation.getParameter(index)));
		return null;
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("passes an argument to a consumer");
	}

}
