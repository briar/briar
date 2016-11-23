package org.briarproject.briar.android.navdrawer;

import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.android.DestroyableContext;

interface TransportStateListener extends DestroyableContext {

	void stateUpdate(TransportId id, boolean enabled);
}
