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

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
public class ConfigControllerImpl implements ConfigController {

	private static final Logger LOG =
			Logger.getLogger(ConfigControllerImpl.class.getName());

	private static final String PREF_DB_KEY = "key";
	private static final String DB_KEY_FILENAME = "db.key";
	private static final String DB_KEY_BACKUP_FILENAME = "db.key.bak";

	private final SharedPreferences briarPrefs;
	private final File dbKeyFile, dbKeyBackupFile;
	protected final DatabaseConfig databaseConfig;

	@Inject
	public ConfigControllerImpl(SharedPreferences briarPrefs,
			DatabaseConfig databaseConfig) {
		this.briarPrefs = briarPrefs;
		this.databaseConfig = databaseConfig;
		File keyDir = databaseConfig.getDatabaseKeyDirectory();
		dbKeyFile = new File(keyDir, DB_KEY_FILENAME);
		dbKeyBackupFile = new File(keyDir, DB_KEY_BACKUP_FILENAME);
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
		String key = readDbKeyFromFile(dbKeyFile);
		if (key == null) {
			LOG.info("No database key in primary file");
			key = readDbKeyFromFile(dbKeyBackupFile);
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
			logException(LOG, WARNING, e);
			return null;
		}
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
		// Create the directory if necessary
		if (databaseConfig.getDatabaseKeyDirectory().mkdirs())
			LOG.info("Created database key directory");
		// If only the backup file exists, rename it so we don't overwrite it
		if (dbKeyBackupFile.exists() && !dbKeyFile.exists()) {
			if (dbKeyBackupFile.renameTo(dbKeyFile))
				LOG.info("Renamed old backup");
			else LOG.warning("Failed to rename old backup");
		}
		try {
			// Write to the backup file
			writeDbKeyToFile(hex, dbKeyBackupFile);
			LOG.info("Stored database key in backup file");
			// Delete the old primary file, if it exists
			if (dbKeyFile.exists()) {
				if (dbKeyFile.delete()) LOG.info("Deleted primary file");
				else LOG.warning("Failed to delete primary file");
			}
			// The backup file becomes the new primary
			if (dbKeyBackupFile.renameTo(dbKeyFile)) {
				LOG.info("Renamed backup file to primary");
			} else {
				LOG.warning("Failed to rename backup file to primary");
				return false; // Don't overwrite our only copy
			}
			// Write a second copy to the backup file
			writeDbKeyToFile(hex, dbKeyBackupFile);
			LOG.info("Stored second copy of database key in backup file");
			return true;
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			return false;
		}
	}

	private void writeDbKeyToFile(String key, File f) throws IOException {
		FileOutputStream out = new FileOutputStream(f);
		out.write(key.getBytes("UTF-8"));
		out.flush();
		out.close();
	}

	@Override
	public void deleteAccount(Context ctx) {
		LOG.info("Deleting account");
		SharedPreferences defaultPrefs =
				PreferenceManager.getDefaultSharedPreferences(ctx);
		AndroidUtils.deleteAppData(ctx, briarPrefs, defaultPrefs);
	}

	@Override
	public boolean accountExists() {
		String hex = getEncryptedDatabaseKey();
		return hex != null && databaseConfig.databaseExists();
	}

	@Override
	public boolean accountSignedIn() {
		return databaseConfig.getEncryptionKey() != null;
	}
}
