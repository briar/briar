package org.briarproject.bramble.account;

import android.app.Application;
import android.content.SharedPreferences;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.util.StringUtils.toHexString;

public class AndroidAccountManagerTest extends BrambleMockTestCase {

	private final SharedPreferences prefs =
			context.mock(SharedPreferences.class);
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final SharedPreferences.Editor
			editor = context.mock(SharedPreferences.Editor.class);
	private final Application app;

	private final String encryptedKeyHex = toHexString(getRandomBytes(123));
	private final File testDir = getTestDirectory();
	private final File keyDir = new File(testDir, "key");
	private final File keyFile = new File(keyDir, "db.key");
	private final File keyBackupFile = new File(keyDir, "db.key.bak");

	private AndroidAccountManager accountManager;

	public AndroidAccountManagerTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		app = context.mock(Application.class);
	}

	@Before
	public void setUp() {
		context.checking(new Expectations() {{
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
			allowing(app).getApplicationContext();
			will(returnValue(app));
		}});
		accountManager = new AndroidAccountManager(databaseConfig, crypto,
				prefs, app);
	}

	@Test
	public void testDbKeyIsMigratedFromPreferencesToFile() {
		context.checking(new Expectations() {{
			oneOf(prefs).getString("key", null);
			will(returnValue(encryptedKeyHex));
			oneOf(prefs).edit();
			will(returnValue(editor));
			oneOf(editor).remove("key");
			will(returnValue(editor));
			oneOf(editor).commit();
			will(returnValue(true));
		}});

		assertFalse(keyFile.exists());
		assertFalse(keyBackupFile.exists());

		assertEquals(encryptedKeyHex, accountManager.loadEncryptedDatabaseKey());

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}
}
