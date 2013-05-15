package net.sf.briar.api.plugins;

import java.util.Collection;

import net.sf.briar.api.lifecycle.Service;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;

/**
 * Responsible for starting transport plugins at startup, stopping them at
 * shutdown, and providing access to plugins for exchanging invitations.
 */
public interface PluginManager extends Service {

	/** Returns any running duplex plugins that support invitations. */
	Collection<DuplexPlugin> getInvitationPlugins();
}
