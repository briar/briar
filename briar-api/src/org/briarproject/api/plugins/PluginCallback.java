package org.briarproject.api.plugins;

import java.util.Map;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportProperties;

/**
 * An interface through which a transport plugin interacts with the rest of
 * the application.
 */
public interface PluginCallback {

	/** Returns the plugin's configuration. */
	TransportConfig getConfig();

	/** Returns the plugin's local transport properties. */
	TransportProperties getLocalProperties();

	/** Returns the plugin's remote transport properties. */
	Map<ContactId, TransportProperties> getRemoteProperties();

	/** Merges the given configuration with the plugin's configuration. */
	void mergeConfig(TransportConfig c);

	/**
	 * Merges the given properties with the plugin's local transport properties.
	 */
	void mergeLocalProperties(TransportProperties p);

	/**
	 * Presents the user with a choice among two or more named options and
	 * returns the user's response. The message may consist of a translatable
	 * format string and arguments.
	 * @return an index into the array of options indicating the user's choice,
	 * or -1 if the user cancelled the choice.
	 */
	int showChoice(String[] options, String... message);

	/**
	 * Asks the user to confirm an action and returns the user's response. The
	 * message may consist of a translatable format string and arguments.
	 */
	boolean showConfirmationMessage(String... message);

	/**
	 * Shows a message to the user. The message may consist of a translatable
	 * format string and arguments.
	 */
	void showMessage(String... message);
}
