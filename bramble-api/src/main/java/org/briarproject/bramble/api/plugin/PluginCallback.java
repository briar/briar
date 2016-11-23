package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.settings.Settings;

import java.util.Map;

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
	 * Returns the plugin's remote transport properties.
	 */
	Map<ContactId, TransportProperties> getRemoteProperties();

	/**
	 * Merges the given settings with the namespaced settings
	 */
	void mergeSettings(Settings s);

	/**
	 * Merges the given properties with the plugin's local transport properties.
	 */
	void mergeLocalProperties(TransportProperties p);

	/**
	 * Presents the user with a choice among two or more named options and
	 * returns the user's response. The message may consist of a translatable
	 * format string and arguments.
	 *
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

	/**
	 * Signal that the transport got enabled.
	 */
	void transportEnabled();

	/**
	 * Signal that the transport got disabled.
	 */
	void transportDisabled();
}
