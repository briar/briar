package net.sf.briar.plugins;

import java.util.Collection;

import net.sf.briar.api.plugins.Plugin;

interface Poller {

	/** Starts a new thread to poll the given collection of plugins. */
	void start(Collection<Plugin> plugins);

	/** Tells the poller thread to exit. */
	void stop();
}
