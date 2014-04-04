package org.briarproject.plugins;

import java.util.Collection;

import org.briarproject.api.plugins.Plugin;

interface Poller {

	/** Starts a poller for the given collection of plugins. */
	void start(Collection<Plugin> plugins);

	/** Stops the poller. */
	void stop();

	/** Tells the poller to poll the given plugin immediately. */
	void pollNow(Plugin p);
}
