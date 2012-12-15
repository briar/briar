package net.sf.briar.plugins.modem;

import net.sf.briar.BriarTestCase;

import org.junit.Test;

public class CountryCodesTest extends BriarTestCase {

	@Test
	public void testTranslation() {
		// Unknown country for caller
		assertNull(CountryCodes.translate("02012345678", "ZZ", "GB"));
		// Unknown country for callee
		assertNull(CountryCodes.translate("02012345678", "GB", "ZZ"));
		// GB to GB, callee has included NDD prefix
		assertEquals("02012345678",
				CountryCodes.translate("02012345678", "GB", "GB"));
		// GB to GB, callee has included IDD prefix and country code
		assertEquals("02012345678",
				CountryCodes.translate("00442012345678", "GB", "GB"));
		// GB to GB, callee has not included a prefix
		assertEquals("02012345678",
				CountryCodes.translate("2012345678", "GB", "GB"));
		// Russia to GB, callee has included NDD prefix
		assertEquals("8**10442012345678",
				CountryCodes.translate("02012345678", "RU", "GB"));
		// Russia to GB, callee has included IDD prefix and country code
		assertEquals("8**10442012345678",
				CountryCodes.translate("00442012345678", "RU", "GB"));
		// Russia to GB, callee has not included a prefix
		assertEquals("8**10442012345678",
				CountryCodes.translate("2012345678", "RU", "GB"));
	}
}
