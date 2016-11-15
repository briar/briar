package org.briarproject.android.controller.handler;

import org.briarproject.android.DestroyableContext;

public interface DestroyableContextManager extends DestroyableContext {

	void addContextResultHandler(ContextResultHandler crh);

	void removeContextResultHandler(String tag);

	boolean containsContextResultHandler(String tag);
}
