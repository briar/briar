package org.briarproject.api.plugins;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexPlugin;

import java.util.Collection;

/**
 * Responsible for starting transport plugins at startup, stopping them at
 * shutdown, and providing access to plugins for exchanging invitations.
 */
public interface PluginManager {

	/**
	 * Returns the plugin for the given transport, or null if no such plugin
	 * is running.
	 */
	Plugin getPlugin(TransportId t);

	/** Returns any running duplex plugins that support invitations. */
	Collection<DuplexPlugin> getInvitationPlugins();
}
