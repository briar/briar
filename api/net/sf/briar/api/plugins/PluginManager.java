package net.sf.briar.api.plugins;

public interface PluginManager {

	/**
	 * Starts all the plugins the manager knows about and returns the number of
	 * plugins successfully started.
	 */
	int startPlugins();

	/**
	 * Stops all the plugins started by startPlugins() and returns the number
	 * of plugins successfully stopped.
	 */
	int stopPlugins();
}
