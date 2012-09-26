package net.sf.briar.api.plugins;

import java.util.Collection;

import net.sf.briar.api.plugins.duplex.DuplexPlugin;

public interface PluginManager {

	/**
	 * Starts the plugins and returns the number of plugins successfully
	 * started. This method must not be called until the database has been
	 * opened.
	 */
	int start();

	/**
	 * Stops the plugins and returns the number of plugins successfully stopped.
	 */
	int stop();

	/** Returns any duplex plugins that support invitations. */
	Collection<DuplexPlugin> getInvitationPlugins();
}
