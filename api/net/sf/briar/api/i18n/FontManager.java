package net.sf.briar.api.i18n;

import java.awt.Font;
import java.util.Locale;

public interface FontManager {

	/** Initializes the FontManager for the given locale. */
	void initialize(Locale locale);

	/** Returns the appropriate font for the given language. */
	Font getFontForLanguage(String language);

	/** Returns the current user interface font. */
	Font getUiFont();

	/** Sets the user interface font appropriately for the given language. */
	void setUiFontForLanguage(String language);
}