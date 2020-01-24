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
	 * Returns any duplex plugins that support key agreement.
	 */
	Collection<DuplexPlugin> getKeyAgreementPlugins();

	/**
	 * Returns any duplex plugins that support rendezvous.
	 */
	Collection<DuplexPlugin> getRendezvousPlugins();

	/**
	 * Enables or disables the plugin
	 * identified by the given {@link TransportId}.
	 * <p>
	 * Note that this applies the change asynchronously
	 * and there are no order guarantees.
	 * <p>
	 * If no plugin with the given {@link TransportId} is registered,
	 * this is a no-op.
	 */
	void setPluginEnabled(TransportId t, boolean enabled);

}
