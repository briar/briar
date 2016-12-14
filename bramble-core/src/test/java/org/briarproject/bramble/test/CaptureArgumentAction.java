package org.briarproject.bramble.test;

import org.hamcrest.Description;
import org.jmock.api.Action;
import org.jmock.api.Invocation;

import java.util.concurrent.atomic.AtomicReference;

public class CaptureArgumentAction<T> implements Action {

	private final AtomicReference<T> captured;
	private final Class<T> capturedClass;
	private final int index;

	public CaptureArgumentAction(AtomicReference<T> captured,
			Class<T> capturedClass, int index) {
		this.captured = captured;
		this.capturedClass = capturedClass;
		this.index = index;
	}

	@Override
	public Object invoke(Invocation invocation) throws Throwable {
		captured.set(capturedClass.cast(invocation.getParameter(index)));
		return null;
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("captures an argument");
	}

}
