package org.briarproject.android.controller.handler;

public interface ResultExceptionHandler<R, E extends Exception>
		extends ExceptionHandler<E> {

	void onResult(R result);

}
