package net.sf.briar.api.plugins;

import java.util.Collection;

import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;

public interface PluginManager {

	/**
	 * Starts the plugins and returns the number of plugins successfully
	 * started.
	 */
	int start();

	/**
	 * Stops the plugins and returns the number of plugins successfully stopped.
	 */
	int stop();

	/** Returns any duplex plugins that support invitations. */
	Collection<DuplexPlugin> getDuplexInvitationPlugins();

	/** Returns any simplex plugins that support invitations. */
	Collection<SimplexPlugin> getSimplexInvitationPlugins();
}
