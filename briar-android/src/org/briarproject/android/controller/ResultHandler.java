package org.briarproject.android.controller;

public interface ResultHandler<R, E> {
	void onResult(R result);
	void onException(E exception);
}
