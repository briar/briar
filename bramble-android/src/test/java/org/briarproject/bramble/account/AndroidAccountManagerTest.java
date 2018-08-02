package org.briarproject.bramble.account;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.identity.IdentityManager;
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
			context.mock(SharedPreferences.class, "prefs");
	private final SharedPreferences defaultPrefs =
			context.mock(SharedPreferences.class, "defaultPrefs");
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final SharedPreferences.Editor
			editor = context.mock(SharedPreferences.Editor.class);
	private final Application app;
	private final ApplicationInfo applicationInfo;

	private final String encryptedKeyHex = toHexString(getRandomBytes(123));
	private final File testDir = getTestDirectory();
	private final File keyDir = new File(testDir, "key");
	private final File keyFile = new File(keyDir, "db.key");
	private final File keyBackupFile = new File(keyDir, "db.key.bak");
	private final File dbDir = new File(testDir, "db");

	private AndroidAccountManager accountManager;

	public AndroidAccountManagerTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		app = context.mock(Application.class);
		applicationInfo = new ApplicationInfo();
		applicationInfo.dataDir = testDir.getAbsolutePath();
	}

	@Before
	public void setUp() {
		context.checking(new Expectations() {{
			allowing(databaseConfig).getDatabaseDirectory();
			will(returnValue(dbDir));
			allowing(databaseConfig).getDatabaseKeyDirectory();
			will(returnValue(keyDir));
			allowing(app).getApplicationContext();
			will(returnValue(app));
		}});
		accountManager = new AndroidAccountManager(databaseConfig, crypto,
				identityManager, prefs, app) {
			@Override
			SharedPreferences getDefaultSharedPreferences() {
				return defaultPrefs;
			}
		};
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

		assertEquals(encryptedKeyHex,
				accountManager.loadEncryptedDatabaseKey());

		assertTrue(keyFile.exists());
		assertTrue(keyBackupFile.exists());
	}

	@Test
	public void testDeleteAccountClearsSharedPrefsAndDeletesFiles()
			throws Exception {
		// Directories 'lib' and 'shared_prefs' should be spared
		File libDir = new File(testDir, "lib");
		File libFile = new File(libDir, "file");
		File sharedPrefsDir = new File(testDir, "shared_prefs");
		File sharedPrefsFile = new File(sharedPrefsDir, "file");
		// Directory 'cache' should be emptied
		File cacheDir = new File(testDir, "cache");
		File cacheFile = new File(cacheDir, "file");
		// Other directories should be deleted
		File potatoDir = new File(testDir, ".potato");
		File potatoFile = new File(potatoDir, "file");

		context.checking(new Expectations() {{
			oneOf(prefs).edit();
			will(returnValue(editor));
			oneOf(editor).clear();
			will(returnValue(editor));
			oneOf(editor).commit();
			will(returnValue(true));
			oneOf(defaultPrefs).edit();
			will(returnValue(editor));
			oneOf(editor).clear();
			will(returnValue(editor));
			oneOf(editor).commit();
			will(returnValue(true));
			oneOf(app).getApplicationInfo();
			will(returnValue(applicationInfo));
		}});

		assertTrue(dbDir.mkdirs());
		assertTrue(keyDir.mkdirs());
		assertTrue(libDir.mkdirs());
		assertTrue(libFile.createNewFile());
		assertTrue(sharedPrefsDir.mkdirs());
		assertTrue(sharedPrefsFile.createNewFile());
		assertTrue(cacheDir.mkdirs());
		assertTrue(cacheFile.createNewFile());
		assertTrue(potatoDir.mkdirs());
		assertTrue(potatoFile.createNewFile());

		accountManager.deleteAccount();

		assertFalse(dbDir.exists());
		assertFalse(keyDir.exists());
		assertTrue(libDir.exists());
		assertTrue(libFile.exists());
		assertTrue(sharedPrefsDir.exists());
		assertTrue(sharedPrefsFile.exists());
		assertTrue(cacheDir.exists());
		assertFalse(cacheFile.exists());
		assertFalse(potatoDir.exists());
		assertFalse(potatoFile.exists());
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}
}
