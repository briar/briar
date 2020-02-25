package org.briarproject.bramble.account;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.identity.IdentityManager;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

import static android.os.Build.VERSION.SDK_INT;
import static java.util.Arrays.asList;
import static java.util.logging.Level.INFO;
import static org.briarproject.bramble.util.IoUtils.deleteFileOrDir;
import static org.briarproject.bramble.util.LogUtils.logFileOrDir;

class AndroidAccountManager extends AccountManagerImpl
		implements AccountManager {

	private static final Logger LOG =
			Logger.getLogger(AndroidAccountManager.class.getName());

	/**
	 * Directories that shouldn't be deleted when deleting the user's account.
	 */
	private static final List<String> PROTECTED_DIR_NAMES =
			asList("cache", "code_cache", "lib", "shared_prefs");

	protected final Context appContext;
	private final SharedPreferences prefs;

	@Inject
	AndroidAccountManager(DatabaseConfig databaseConfig,
			CryptoComponent crypto, IdentityManager identityManager,
			SharedPreferences prefs, Application app) {
		super(databaseConfig, crypto, identityManager);
		this.prefs = prefs;
		appContext = app.getApplicationContext();
	}

	@Override
	public boolean accountExists() {
		boolean exists = super.accountExists();
		if (!exists && LOG.isLoggable(INFO)) {
			LOG.info("Account does not exist. Contents of account directory:");
			logFileOrDir(LOG, INFO, getDataDir());
		}
		return exists;
	}

	@Override
	public void deleteAccount() {
		synchronized (stateChangeLock) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Contents of account directory before deleting:");
				logFileOrDir(LOG, INFO, getDataDir());
			}
			super.deleteAccount();
			SharedPreferences defaultPrefs = getDefaultSharedPreferences();
			deleteAppData(prefs, defaultPrefs);
			if (LOG.isLoggable(INFO)) {
				LOG.info("Contents of account directory after deleting:");
				logFileOrDir(LOG, INFO, getDataDir());
			}
		}
	}

	// Package access for testing
	SharedPreferences getDefaultSharedPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(appContext);
	}

	@GuardedBy("stateChangeLock")
	private void deleteAppData(SharedPreferences... clear) {
		// Clear and commit shared preferences
		for (SharedPreferences prefs : clear) {
			if (!prefs.edit().clear().commit())
				LOG.warning("Could not clear shared preferences");
		}
		// Delete files, except protected directories
		Set<File> files = new HashSet<>();
		File dataDir = getDataDir();
		@Nullable
		File[] fileArray = dataDir.listFiles();
		if (fileArray == null) {
			LOG.warning("Could not list files in app data dir");
		} else {
			for (File file : fileArray) {
				if (!PROTECTED_DIR_NAMES.contains(file.getName())) {
					files.add(file);
				}
			}
		}
		files.add(appContext.getFilesDir());
		addIfNotNull(files, appContext.getExternalCacheDir());
		if (SDK_INT >= 19) {
			for (File file : appContext.getExternalCacheDirs()) {
				addIfNotNull(files, file);
			}
		}
		if (SDK_INT >= 21) {
			for (File file : appContext.getExternalMediaDirs()) {
				addIfNotNull(files, file);
			}
		}
		// Clear the cache directory but don't delete it
		File cacheDir = appContext.getCacheDir();
		File[] children = cacheDir.listFiles();
		if (children != null) files.addAll(asList(children));
		for (File file : files) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Deleting " + file.getAbsolutePath());
			}
			deleteFileOrDir(file);
		}
	}

	private File getDataDir() {
		return new File(appContext.getApplicationInfo().dataDir);
	}

	private void addIfNotNull(Set<File> files, @Nullable File file) {
		if (file != null) files.add(file);
	}
}
