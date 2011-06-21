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

import javax.swing.UIManager;

import net.sf.briar.api.i18n.FontManager;
import net.sf.briar.util.FileUtils;

public class FontManagerImpl implements FontManager {

	private static final BundledFont[] BUNDLED_FONTS = {
		new BundledFont("TibetanMachineUni.ttf", 14f, new String[] { "bo" }),
		new BundledFont("Padauk.ttf", 14f, new String[] { "my" }),
	};

	private final Map<String, Font> fonts = new TreeMap<String, Font>();

	private volatile Font defaultFont = null, uiFont = null;

	public void initialize(Locale locale) throws IOException {
		try {
			ClassLoader loader = getClass().getClassLoader();
			for(BundledFont bf : BUNDLED_FONTS) {
				InputStream in = loader.getResourceAsStream(bf.filename);
				if(in == null) {
					File root = FileUtils.getBriarDirectory();
					File file = new File(root, "Data/" + bf.filename);
					in = new FileInputStream(file);
				}
				Font font = Font.createFont(Font.TRUETYPE_FONT, in);
				font = font.deriveFont(bf.size);
				for(String language : bf.languages) fonts.put(language, font);
			}
		} catch(FontFormatException e) {
			throw new IOException(e);
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

	public String[] getBundledFontFilenames() {
		String[] names = new String[BUNDLED_FONTS.length];
		for(int i = 0; i < BUNDLED_FONTS.length; i++)
			names[i] = BUNDLED_FONTS[i].filename;
		return names;
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
