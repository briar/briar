package net.sf.briar.api.i18n;

import java.awt.ComponentOrientation;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public interface I18n {

	String tr(String name);

	Locale getLocale();

	void setLocale(Locale locale);

	void loadLocale() throws IOException;

	void saveLocale() throws IOException;

	void saveLocale(File dir) throws IOException;

	ComponentOrientation getComponentOrientation();

	void addListener(Listener l);

	void removeListener(Listener l);

	public interface Listener {

		void localeChanged(Font uiFont);
	}
}