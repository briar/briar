package org.briarproject.briar.android.navdrawer;

import androidx.annotation.UiThread;

import org.briarproject.bramble.api.plugin.TransportId;

interface TransportStateListener {

	@UiThread
	void stateUpdate(TransportId id, boolean enabled);
}
