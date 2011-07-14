package net.sf.briar.api.i18n;

import java.awt.Font;
import java.io.File;
import java.util.Locale;

public interface FontManager {

	/**
	 * Initializes the FontManager for the given locale. Fonts are loaded from
	 * the given directory if they cannot be loaded from the running jar.
	 */
	void initialize(Locale locale, File dir);

	/** Returns the appropriate font for the given language. */
	Font getFontForLanguage(String language);

	/** Returns the current user interface font. */
	Font getUiFont();

	/** Sets the user interface font appropriately for the given language. */
	void setUiFontForLanguage(String language);
}