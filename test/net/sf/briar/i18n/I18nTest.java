package net.sf.briar.i18n;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.i18n.FontManager;
import net.sf.briar.api.i18n.I18n;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class I18nTest extends TestCase {

	final File base =
		new File(TestUtils.getBuildDirectory(), "i18n.properties");
	final File french =
		new File(TestUtils.getBuildDirectory(), "i18n_fr.properties");
	final File testDir = TestUtils.getTestDirectory();

	FontManager fontManager = null;
	I18n i18n = null;

	@Before
	public void setUp() throws IOException {
		TestUtils.createFile(base,
				"FOO=foo\r\n" +
				"BAR=bar\r\n");
		TestUtils.createFile(french,
				"FOO=le foo\r\n" +
				"BAR=la bar\r\n");
		fontManager = new FontManagerImpl();
		fontManager.initialize(Locale.UK);
		i18n = new I18nImpl(fontManager);
	}

	@Test
	public void testTr() {
		i18n.setLocale(Locale.UK);
		assertEquals("foo", i18n.tr("FOO"));
		i18n.setLocale(Locale.FRANCE);
		assertEquals("le foo", i18n.tr("FOO"));
		i18n.setLocale(Locale.CHINA); // No translation - use defaul
		assertEquals("foo", i18n.tr("FOO"));
	}

	@Test
	public void testSettingLocaleAffectsComponentOrientation() {
		i18n.setLocale(new Locale("en")); // English
		assertTrue(i18n.getComponentOrientation().isLeftToRight());
		i18n.setLocale(new Locale("ar")); // Arabic
		assertFalse(i18n.getComponentOrientation().isLeftToRight());
	}

	@Test
	public void testListenersAreInformedOfLocaleChanges() {
		final Font englishFont = fontManager.getFontForLanguage("en");
		final Font tibetanFont = fontManager.getFontForLanguage("bo");

		Mockery context = new Mockery();
		final I18n.Listener listener = context.mock(I18n.Listener.class);
		context.checking(new Expectations() {{
			// Listener should be called once when registered
			oneOf(listener).localeChanged(englishFont);
			// Listener should be called again when locale changes
			oneOf(listener).localeChanged(tibetanFont);
		}});

		i18n.setLocale(new Locale("en"));
		i18n.addListener(listener);
		i18n.setLocale(new Locale("bo"));
		i18n.removeListener(listener);

		context.assertIsSatisfied();
	}

	@Test
	public void testSaveAndLoadLocale() throws IOException {
		testDir.mkdirs();
		new File(testDir, "Data").mkdir();
		i18n.setLocale(new Locale("fr"));
		assertEquals("le foo", i18n.tr("FOO"));
		i18n.saveLocale();
		i18n.setLocale(new Locale("zh")); // No translation - use default
		assertEquals("foo", i18n.tr("FOO"));
		i18n.loadLocale();
		assertEquals("le foo", i18n.tr("FOO"));
	}

	@After
	public void tearDown() {
		TestUtils.delete(base);
		TestUtils.delete(french);
		TestUtils.deleteTestDirectory(testDir);
	}
}
