package org.briarproject.android.controller;

import org.briarproject.api.TransportId;

public interface TransportStateListener {

	void stateUpdate(TransportId id, boolean enabled);
}
