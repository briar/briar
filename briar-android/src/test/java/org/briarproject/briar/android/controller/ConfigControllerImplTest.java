package org.briarproject.briar.android.controller;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.util.StringUtils.toHexString;

public class ConfigControllerImplTest extends BrambleMockTestCase {

	private final SharedPreferences prefs =
			context.mock(SharedPreferences.class);
	private final AccountManager accountManager =
			context.mock(AccountManager.class);
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final Editor editor = context.mock(Editor.class);

	private final String encryptedKeyHex = toHexString(getRandomBytes(123));

	@Test
	public void testDbKeyIsMigratedFromPreferencesToFile() {
		context.checking(new Expectations() {{
			oneOf(prefs).getString("key", null);
			will(returnValue(encryptedKeyHex));
			oneOf(accountManager).storeEncryptedDatabaseKey(encryptedKeyHex);
			will(returnValue(true));
			oneOf(prefs).edit();
			will(returnValue(editor));
			oneOf(editor).remove("key");
			will(returnValue(editor));
			oneOf(editor).commit();
			will(returnValue(true));
		}});

		ConfigControllerImpl c = new ConfigControllerImpl(prefs, accountManager,
				databaseConfig);

		assertEquals(encryptedKeyHex, c.getEncryptedDatabaseKey());
	}

}
