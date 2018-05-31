package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.settings.Settings;

/**
 * An interface through which a transport plugin interacts with the rest of
 * the application.
 */
@NotNullByDefault
public interface PluginCallback {

	/**
	 * Returns the plugin's settings
	 */
	Settings getSettings();

	/**
	 * Returns the plugin's local transport properties.
	 */
	TransportProperties getLocalProperties();

	/**
	 * Merges the given settings with the plugin's settings
	 */
	void mergeSettings(Settings s);

	/**
	 * Merges the given properties with the plugin's local transport properties.
	 */
	void mergeLocalProperties(TransportProperties p);

	/**
	 * Signals that the transport is enabled.
	 */
	void transportEnabled();

	/**
	 * Signals that the transport is disabled.
	 */
	void transportDisabled();
}
