package org.briarproject.api.plugins;

import java.util.Collection;

import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.plugins.duplex.DuplexPlugin;

/**
 * Responsible for starting transport plugins at startup, stopping them at
 * shutdown, and providing access to plugins for exchanging invitations.
 */
public interface PluginManager extends Service {

	/** Returns any running duplex plugins that support invitations. */
	Collection<DuplexPlugin> getInvitationPlugins();
}
