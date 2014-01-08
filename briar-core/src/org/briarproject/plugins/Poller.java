package org.briarproject.plugins;

import java.util.Collection;

import org.briarproject.api.plugins.Plugin;

interface Poller {

	/** Starts a new thread to poll the given collection of plugins. */
	void start(Collection<Plugin> plugins);

	/** Tells the poller thread to exit. */
	void stop();
}
