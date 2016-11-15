package org.briarproject.android.controller.handler;

public interface ContextResultHandler<R> extends ResultHandler<R> {
	void setDestroyableContextManager(DestroyableContextManager listener);
	String getTag();
	DestroyableContextManager getContextManager();
}
