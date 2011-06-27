package net.sf.briar.api.i18n;

import java.awt.ComponentOrientation;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public interface I18n {

	/** Returns the named string, translated for the current i18n locale. */
	String tr(String name);

	/** Returns the i18n locale. This may not match the system locale. */
	Locale getLocale();

	/** Sets the i18n locale. */
	void setLocale(Locale locale);

	/** Loads the i18n locale from Briar/Data/locale.cfg. */
	void loadLocale() throws IOException;

	/** Saves the i18n locale to Briar/Data/locale.cfg. */
	void saveLocale() throws IOException;

	/** Saves the i18n locale to the given file. */
	void saveLocale(File dir) throws IOException;

	/** Returns the ComponentOrientation of the current i18n locale. */
	ComponentOrientation getComponentOrientation();

	/** Registers a listener for changes to the i18n locale. */
	void addListener(Listener l);

	/** Unregisters a listener for changes to the i18n locale. */
	void removeListener(Listener l);

	/**
	 * Implemented by classes that wish to be informed of changes to the i18n
	 * locale.
	 */
	public interface Listener {

		/**
		 * Called whenever the i18n locale changes.
		 * @param uiFont The user interface font for the new locale.
		 */
		void localeChanged(Font uiFont);
	}
}