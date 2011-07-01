package net.sf.briar.i18n;
import java.awt.Font;
import java.util.Locale;

import junit.framework.TestCase;
import net.sf.briar.i18n.FontManagerImpl;

import org.junit.Test;

public class FontManagerImplTest extends TestCase {

	@Test
	public void testBundledFontsAreLoaded() {
		FontManagerImpl fontManager = new FontManagerImpl();
		fontManager.initialize(Locale.UK);

		Font font = fontManager.getFontForLanguage("en"); // English
		assertEquals(12, font.getSize());

		font = fontManager.getFontForLanguage("bo"); // Tibetan
		assertEquals("Tibetan Machine Uni", font.getFamily());
		assertEquals(14, font.getSize());

		font = fontManager.getFontForLanguage("my"); // Burmese
		assertEquals("Padauk", font.getFamily());
		assertEquals(14, font.getSize());
	}

	@Test
	public void testInternationalCharactersCanBeDisplayed() {
		FontManagerImpl fontManager = new FontManagerImpl();
		fontManager.initialize(Locale.UK);

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
		FontManagerImpl fontManager = new FontManagerImpl();
		fontManager.initialize(Locale.UK);
		Font font = fontManager.getUiFont();
		assertEquals(12, font.getSize());

		fontManager.setUiFontForLanguage("bo");
		font = fontManager.getUiFont();
		assertEquals("Tibetan Machine Uni", font.getFamily());
		assertEquals(14, font.getSize());

		fontManager.setUiFontForLanguage("en");
		font = fontManager.getUiFont();
		assertEquals(12, font.getSize());
	}
}
