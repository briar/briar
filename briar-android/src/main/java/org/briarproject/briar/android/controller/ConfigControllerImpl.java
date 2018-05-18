package org.briarproject.briar.android.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.AndroidUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@NotNullByDefault
public class ConfigControllerImpl implements ConfigController {

	private static final Logger LOG =
			Logger.getLogger(ConfigControllerImpl.class.getName());

	private static final String PREF_DB_KEY = "key";
	private static final String DB_KEY_FILENAME = "db.key";
	private static final String DB_KEY_BACKUP_FILENAME = "db.key.bak";

	private final SharedPreferences briarPrefs;
	protected final DatabaseConfig databaseConfig;

	@Inject
	public ConfigControllerImpl(SharedPreferences briarPrefs,
			DatabaseConfig databaseConfig) {
		this.briarPrefs = briarPrefs;
		this.databaseConfig = databaseConfig;
	}

	@Override
	@Nullable
	public String getEncryptedDatabaseKey() {
		String key = getDatabaseKeyFromPreferences();
		if (key == null) key = getDatabaseKeyFromFile();
		else migrateDatabaseKeyToFile(key);
		return key;
	}

	@Nullable
	private String getDatabaseKeyFromPreferences() {
		String key = briarPrefs.getString(PREF_DB_KEY, null);
		if (key == null) LOG.info("No database key in preferences");
		else LOG.info("Found database key in preferences");
		return key;
	}

	@Nullable
	private String getDatabaseKeyFromFile() {
		String key = readDbKeyFromFile(getDbKeyFile());
		if (key == null) {
			LOG.info("No database key in primary file");
			key = readDbKeyFromFile(getDbKeyBackupFile());
			if (key == null) LOG.info("No database key in backup file");
			else LOG.warning("Found database key in backup file");
		} else {
			LOG.info("Found database key in primary file");
		}
		return key;
	}

	@Nullable
	private String readDbKeyFromFile(File f) {
		if (!f.exists()) {
			LOG.info("Key file does not exist");
			return null;
		}
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(f), "UTF-8"));
			String key = reader.readLine();
			reader.close();
			return key;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private File getDbKeyFile() {
		return new File(databaseConfig.getDatabaseKeyDirectory(),
				DB_KEY_FILENAME);
	}

	private File getDbKeyBackupFile() {
		return new File(databaseConfig.getDatabaseKeyDirectory(),
				DB_KEY_BACKUP_FILENAME);
	}

	private void migrateDatabaseKeyToFile(String key) {
		if (storeEncryptedDatabaseKey(key)) {
			if (briarPrefs.edit().remove(PREF_DB_KEY).commit())
				LOG.info("Database key migrated to file");
			else LOG.warning("Database key not removed from preferences");
		} else {
			LOG.warning("Database key not migrated to file");
		}
	}

	@Override
	public boolean storeEncryptedDatabaseKey(String hex) {
		LOG.info("Storing database key in file");
		File dbKey = getDbKeyFile();
		File dbKeyBackup = getDbKeyBackupFile();
		try {
			// Create the directory if necessary
			if (databaseConfig.getDatabaseKeyDirectory().mkdirs())
				LOG.info("Created database key directory");
			// Write to the backup file
			FileOutputStream out = new FileOutputStream(dbKeyBackup);
			out.write(hex.getBytes("UTF-8"));
			out.flush();
			out.close();
			LOG.info("Stored database key in backup file");
			// Delete the old key file, if it exists
			if (dbKey.exists()) {
				if (dbKey.delete()) LOG.info("Deleted primary file");
				else LOG.warning("Failed to delete primary file");
			}
			// The backup file becomes the new key file
			boolean renamed = dbKeyBackup.renameTo(dbKey);
			if (renamed) LOG.info("Renamed backup file to primary");
			else LOG.warning("Failed to rename backup file to primary");
			return renamed;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return false;
		}
	}

	@Override
	public void deleteAccount(Context ctx) {
		LOG.info("Deleting account");
		SharedPreferences defaultPrefs =
				PreferenceManager.getDefaultSharedPreferences(ctx);
		AndroidUtils.deleteAppData(ctx, briarPrefs, defaultPrefs);
		AndroidUtils.logDataDirContents(ctx);
	}

	@Override
	public boolean accountExists() {
		String hex = getEncryptedDatabaseKey();
		boolean exists = hex != null && databaseConfig.databaseExists();
		if (LOG.isLoggable(INFO)) LOG.info("Account exists: " + exists);
		return exists;
	}

	@Override
	public boolean accountSignedIn() {
		boolean signedIn = databaseConfig.getEncryptionKey() != null;
		if (LOG.isLoggable(INFO)) LOG.info("Signed in: " + signedIn);
		return signedIn;
	}
}
