package org.briarproject.plugins;

import org.briarproject.api.plugins.Plugin;

interface Poller {

	/** Tells the poller to poll the given plugin immediately. */
	void pollNow(Plugin p);
}
