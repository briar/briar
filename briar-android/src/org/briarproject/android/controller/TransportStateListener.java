package org.briarproject.android.controller;

import org.briarproject.android.DestroyableContext;
import org.briarproject.api.TransportId;

public interface TransportStateListener extends DestroyableContext {

	void stateUpdate(TransportId id, boolean enabled);
}
