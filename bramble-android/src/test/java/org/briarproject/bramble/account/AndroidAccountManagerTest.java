package org.briarproject.bramble.account;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.logging.PersistentLogManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.logging.Logger;

import static android.content.Context.MODE_PRIVATE;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;

public class AndroidAccountManagerTest extends BrambleMockTestCase {

	private final SharedPreferences prefs =
			context.mock(SharedPreferences.class, "prefs");
	private final SharedPreferences defaultPrefs =
			context.mock(SharedPreferences.class, "defaultPrefs");
	private final PersistentLogManager logManager =
			context.mock(PersistentLogManager.class);
	private final FeatureFlags featureFlags =
			context.mock(FeatureFlags.class);
	private final DatabaseConfig databaseConfig =
			context.mock(DatabaseConfig.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final SharedPreferences.Editor
			editor = context.mock(SharedPreferences.Editor.class);
	private final Application app;
	private final ApplicationInfo applicationInfo;

	private final File testDir = getTestDirectory();
	private final File keyDir = new File(testDir, "key");
	private final File dbDir = new File(testDir, "db");
	private final File logDir = new File(testDir, "log");

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
				identityManager, prefs, logManager, featureFlags, app) {
			@Override
			SharedPreferences getDefaultSharedPreferences() {
				return defaultPrefs;
			}
		};
	}

	@Test
	public void testDeleteAccountClearsSharedPrefsAndDeletesFiles()
			throws Exception {
		// Directories 'code_cache', 'lib' and 'shared_prefs' should be spared
		File codeCacheDir = new File(testDir, "code_cache");
		File codeCacheFile = new File(codeCacheDir, "file");
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
		File filesDir = new File(testDir, "filesDir");
		File externalCacheDir = new File(testDir, "externalCacheDir");

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
			allowing(app).getApplicationInfo();
			will(returnValue(applicationInfo));
			oneOf(app).getFilesDir();
			will(returnValue(filesDir));
			oneOf(app).getCacheDir();
			will(returnValue(cacheDir));
			oneOf(app).getExternalCacheDir();
			will(returnValue(externalCacheDir));
			oneOf(featureFlags).shouldEnablePersistentLogs();
			will(returnValue(true));
			oneOf(app).getDir("log", MODE_PRIVATE);
			will(returnValue(logDir));
			oneOf(logManager).addLogHandler(with(logDir),
					with(any(Logger.class)));
		}});

		assertTrue(dbDir.mkdirs());
		assertTrue(keyDir.mkdirs());
		assertTrue(logDir.mkdirs());
		assertTrue(codeCacheDir.mkdirs());
		assertTrue(codeCacheFile.createNewFile());
		assertTrue(libDir.mkdirs());
		assertTrue(libFile.createNewFile());
		assertTrue(sharedPrefsDir.mkdirs());
		assertTrue(sharedPrefsFile.createNewFile());
		assertTrue(cacheDir.mkdirs());
		assertTrue(cacheFile.createNewFile());
		assertTrue(potatoDir.mkdirs());
		assertTrue(potatoFile.createNewFile());
		assertTrue(filesDir.mkdirs());
		assertTrue(externalCacheDir.mkdirs());

		accountManager.deleteAccount();

		assertFalse(dbDir.exists());
		assertFalse(keyDir.exists());
		assertFalse(logDir.exists());
		assertTrue(codeCacheDir.exists());
		assertTrue(codeCacheFile.exists());
		assertTrue(libDir.exists());
		assertTrue(libFile.exists());
		assertTrue(sharedPrefsDir.exists());
		assertTrue(sharedPrefsFile.exists());
		assertTrue(cacheDir.exists());
		assertFalse(cacheFile.exists());
		assertFalse(potatoDir.exists());
		assertFalse(potatoFile.exists());
		assertFalse(filesDir.exists());
		assertFalse(externalCacheDir.exists());
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}
}
