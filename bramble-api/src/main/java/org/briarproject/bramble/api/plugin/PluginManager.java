package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;

import java.util.Collection;

import javax.annotation.Nullable;

/**
 * Responsible for starting transport plugins at startup and stopping them at
 * shutdown.
 */
@NotNullByDefault
public interface PluginManager {

	/**
	 * Returns the plugin for the given transport, or null if no such plugin
	 * has been created.
	 */
	@Nullable
	Plugin getPlugin(TransportId t);

	/**
	 * Returns any simplex plugins that have been created.
	 */
	Collection<SimplexPlugin> getSimplexPlugins();

	/**
	 * Returns any duplex plugins that have been created.
	 */
	Collection<DuplexPlugin> getDuplexPlugins();

	/**
	 * Returns any duplex plugins that support invitations.
	 */
	Collection<DuplexPlugin> getInvitationPlugins();

	/**
	 * Returns any duplex plugins that support key agreement.
	 */
	Collection<DuplexPlugin> getKeyAgreementPlugins();
}
