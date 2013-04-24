package net.sf.briar.api.plugins;

import java.util.Collection;

import net.sf.briar.api.plugins.duplex.DuplexPlugin;

/**
 * Responsible for starting transport plugins at startup, stopping them at
 * shutdown, and providing access to plugins for exchanging invitations.
 */
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

	/** Returns any running duplex plugins that support invitations. */
	Collection<DuplexPlugin> getInvitationPlugins();
}
