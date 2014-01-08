package org.briarproject.plugins.modem;

import org.briarproject.BriarTestCase;

import org.junit.Test;

public class CountryCodesTest extends BriarTestCase {

	@Test
	public void testTranslation() {
		// Unrecognised country for caller
		assertNull(CountryCodes.translate("02012345678", "ZZ", "GB"));
		// Unrecognised country for callee
		assertNull(CountryCodes.translate("02012345678", "GB", "ZZ"));

		// GB to GB, callee has not included a prefix
		assertEquals("02012345678",
				CountryCodes.translate("2012345678", "GB", "GB"));
		// GB to GB, callee has included NDD prefix
		assertEquals("02012345678",
				CountryCodes.translate("02012345678", "GB", "GB"));
		// GB to GB, callee has included plus sign and country code
		assertEquals("02012345678",
				CountryCodes.translate("+442012345678", "GB", "GB"));
		// GB to GB, callee has included IDD prefix and country code
		assertEquals("02012345678",
				CountryCodes.translate("00442012345678", "GB", "GB"));

		// Russia to GB, callee has not included a prefix
		assertEquals("8**10442012345678",
				CountryCodes.translate("2012345678", "RU", "GB"));
		// Russia to GB, callee has included NDD prefix
		assertEquals("8**10442012345678",
				CountryCodes.translate("02012345678", "RU", "GB"));
		// Russia to GB, callee has included plus sign and country code
		assertEquals("8**10442012345678",
				CountryCodes.translate("+442012345678", "RU", "GB"));
		// Russia to GB, callee has included IDD prefix and country code
		assertEquals("8**10442012345678",
				CountryCodes.translate("00442012345678", "RU", "GB"));

		// Andorra to Andorra (no NDD), callee has not included a prefix
		assertEquals("765432", CountryCodes.translate("765432", "AD", "AD"));
		// Andorra to Andorra, callee has included plus sign and country code
		assertEquals("765432",
				CountryCodes.translate("+376765432", "AD", "AD"));
		// Andorra to Andorra, callee has included IDD and country code
		assertEquals("765432",
				CountryCodes.translate("00376765432", "AD", "AD"));

		// GB to Andorra (no NDD), callee has not included a prefix
		assertEquals("00376765432",
				CountryCodes.translate("765432", "GB", "AD"));
		// GB to Andorra, callee has included plus sign and country code
		assertEquals("00376765432",
				CountryCodes.translate("+376765432", "GB", "AD"));
		// GB to Andorra, callee has included IDD and country code
		assertEquals("00376765432",
				CountryCodes.translate("00376765432", "GB", "AD"));
	}
}
