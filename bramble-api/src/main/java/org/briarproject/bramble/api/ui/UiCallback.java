package org.briarproject.bramble.api.ui;

public interface UiCallback {

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
}
