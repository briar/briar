package net.sf.briar.i18n;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.font.TextAttribute;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.UIManager;

import net.sf.briar.api.i18n.FontManager;
import net.sf.briar.util.FileUtils;

public class FontManagerImpl implements FontManager {

	private static final Logger LOG =
		Logger.getLogger(FontManagerImpl.class.getName());

	/**
	 * Each bundled font is associated with a size, which is meant to occupy
	 * roughly the same amount of space as the default font (12 point sans),
	 * and a list of languages for which the bundled font should be used.
	 */
	private static final BundledFont[] BUNDLED_FONTS = {
		// Use TibetanMachineUni for Tibetan
		new BundledFont("TibetanMachineUni.ttf", 14f, new String[] { "bo" }),
		// Use Padauk for Burmese
		new BundledFont("Padauk.ttf", 14f, new String[] { "my" }),
	};

	// Map from languages to fonts
	private final Map<String, Font> fonts = new TreeMap<String, Font>();

	private volatile Font defaultFont = null, uiFont = null;

	public void initialize(Locale locale) {
		// Look for bundled fonts in the jar and the filesystem. If any fonts
		// are missing or fail to load, fall back to the default font.
		ClassLoader loader = getClass().getClassLoader();
		for(BundledFont bf : BUNDLED_FONTS) {
			try {
				InputStream in = loader.getResourceAsStream(bf.filename);
				if(in == null) {
					File root = FileUtils.getBriarDirectory();
					File file = new File(root, "Data/" + bf.filename);
					in = new FileInputStream(file);
				}
				Font font = Font.createFont(Font.TRUETYPE_FONT, in);
				font = font.deriveFont(bf.size);
				for(String language : bf.languages) fonts.put(language, font);
			} catch(FontFormatException e) {
				if(LOG.isLoggable(Level.WARNING))
					LOG.warning("Could not load font: " + e.getMessage());
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING))
					LOG.warning("Could not load font: " + e.getMessage());
			}
		}
		defaultFont = getFont("Sans", 12f);
		assert defaultFont != null; // FIXME: This is failing on Windows
		setUiFontForLanguage(locale.getLanguage());
	}

	private Font getFont(String name, float size) {
		Map<TextAttribute, Object> attr = new HashMap<TextAttribute, Object>();
		attr.put(TextAttribute.FAMILY, name);
		attr.put(TextAttribute.SIZE, Float.valueOf(size));
		return Font.getFont(attr);
	}

	public Font getFontForLanguage(String language) {
		assert defaultFont != null;
		Font font = fonts.get(language);
		return font == null ? defaultFont : font;
	}

	public Font getUiFont() {
		return uiFont;
	}

	public void setUiFontForLanguage(String language) {
		uiFont = getFontForLanguage(language);
		Enumeration<Object> keys = UIManager.getDefaults().keys();
		while(keys.hasMoreElements()) {
			Object key = keys.nextElement();
			if(UIManager.getFont(key) != null) UIManager.put(key, uiFont);
		}
	}

	private static class BundledFont {

		private final String filename;
		private final float size;
		private final String[] languages;

		BundledFont(String filename, float size, String[] languages) {
			this.filename = filename;
			this.size = size;
			this.languages = languages;
		}
	}
}
