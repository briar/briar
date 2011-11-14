package net.sf.briar.api.plugins;

public interface PluginManager {

	/**
	 * Starts the plugins and returns the number of plugins successfully
	 * started.
	 */
	int startPlugins();

	/**
	 * Stops the plugins and returns the number of plugins successfully stopped.
	 */
	int stopPlugins();
}
