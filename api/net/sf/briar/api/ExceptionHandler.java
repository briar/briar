package net.sf.briar.api;

public interface ExceptionHandler<E extends Exception> {

	void handleException(E exception);
}
