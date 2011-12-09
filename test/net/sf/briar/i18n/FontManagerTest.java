package net.sf.briar.i18n;
import java.awt.Font;
import java.io.File;
import java.util.Locale;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.i18n.FontManager;

import org.junit.Test;

public class FontManagerTest extends BriarTestCase {

	private final File fontDir = TestUtils.getFontDirectory();

	@Test
	public void testBundledFontsAreLoaded() {
		FontManager fontManager = new FontManagerImpl();
		fontManager.initialize(Locale.UK, fontDir);

		Font font = fontManager.getFontForLanguage("en"); // English
		assertEquals(12, font.getSize());

		// The exact font names vary by platform, so just check how they start
		font = fontManager.getFontForLanguage("bo"); // Tibetan
		assertTrue(font.getFamily().startsWith("Tibetan"));
		assertEquals(14, font.getSize());

		font = fontManager.getFontForLanguage("my"); // Burmese
		assertTrue(font.getFamily().startsWith("Padauk"));
		assertEquals(14, font.getSize());
	}

	@Test
	public void testInternationalCharactersCanBeDisplayed() {
		FontManager fontManager = new FontManagerImpl();
		fontManager.initialize(Locale.UK, fontDir);

		Font font = fontManager.getFontForLanguage("en"); // English
		assertTrue(font.canDisplay('a'));

		font = fontManager.getFontForLanguage("ar"); // Arabic
		assertTrue(font.canDisplay('\u0627')); // An Arabic character

		font = fontManager.getFontForLanguage("bo"); // Tibetan
		assertTrue(font.canDisplay('\u0f00')); // A Tibetan character

		font = fontManager.getFontForLanguage("fa"); // Persian
		assertTrue(font.canDisplay('\ufb56')); // A Persian character

		font = fontManager.getFontForLanguage("my"); // Burmese
		assertTrue(font.canDisplay('\u1000')); // A Burmese character

		font = fontManager.getFontForLanguage("zh"); // Chinese
		assertTrue(font.canDisplay('\u4e00')); // A Chinese character
	}

	@Test
	public void testSetAndGetUiFont() {
		FontManager fontManager = new FontManagerImpl();
		fontManager.initialize(Locale.UK, fontDir);
		Font font = fontManager.getUiFont();
		assertEquals(12, font.getSize());

		fontManager.setUiFontForLanguage("bo");
		font = fontManager.getUiFont();
		assertTrue(font.getFamily().startsWith("Tibetan"));
		assertEquals(14, font.getSize());

		fontManager.setUiFontForLanguage("en");
		font = fontManager.getUiFont();
		assertEquals(12, font.getSize());
	}
}
