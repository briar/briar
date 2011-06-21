package net.sf.briar.api.i18n;

import java.awt.Font;
import java.io.IOException;
import java.util.Locale;

public interface FontManager {

	void initialize(Locale locale) throws IOException;

	String[] getBundledFontFilenames();

	Font getFontForLanguage(String language);

	Font getUiFont();

	void setUiFontForLanguage(String language);
}