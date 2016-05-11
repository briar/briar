package org.briarproject.android.controller.handler;

public interface ResultExceptionHandler<R, E extends Exception> {

	void onResult(R result);

	void onException(E exception);
}
