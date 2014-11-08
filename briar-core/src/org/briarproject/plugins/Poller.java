package org.briarproject.plugins;

import org.briarproject.api.plugins.Plugin;

interface Poller {

	/** Adds the given plugin to the collection of plugins to be polled. */
	void addPlugin(Plugin p);

	/** Tells the poller to poll the given plugin immediately. */
	void pollNow(Plugin p);

	/** Stops the poller. */
	void stop();
}
