package org.briarproject.briar.android.account;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static android.os.Environment.getExternalStoragePublicDirectory;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.copyAndClose;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.BriarApplication.ENTRY_ACTIVITY;
import static org.briarproject.briar.android.navdrawer.NavDrawerActivity.SIGN_OUT_URI;

public class AccountUtils {

	private static final Logger LOG = getLogger(AccountUtils.class.getName());

	private static final String[] BACKUP_DIRS =
			{"app_db", "app_key", "shared_prefs"};

	public static void exportAccount(Context ctx) {
		try {
			File dataDir = getDataDir(ctx);
			File backupDir = getBackupDir(ctx);
			for (String name : BACKUP_DIRS) {
				copyRecursively(new File(dataDir, name),
						new File(backupDir, name));
			}
			Toast.makeText(ctx, "Account exported to "
					+ backupDir.getCanonicalPath(), LENGTH_LONG).show();
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			Toast.makeText(ctx, "Export failed", LENGTH_LONG).show();
		}
	}

	public static void importAccount(Context ctx) {
		try {
			File dataDir = getDataDir(ctx);
			File backupDir = getBackupDir(ctx);
			for (String name : BACKUP_DIRS) {
				copyRecursively(new File(backupDir, name),
						new File(dataDir, name));
			}
			Toast.makeText(ctx, "Account imported from "
					+ backupDir.getCanonicalPath(), LENGTH_LONG).show();
			Intent intent = new Intent(ctx, ENTRY_ACTIVITY);
			intent.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
			intent.setData(SIGN_OUT_URI);
			ctx.startActivity(intent);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			Toast.makeText(ctx, "Import failed", LENGTH_LONG).show();
		}
	}

	private static File getDataDir(Context ctx) {
		return new File(ctx.getApplicationInfo().dataDir);
	}

	private static File getBackupDir(Context ctx) {
		File downloads = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
		return new File(downloads, ctx.getPackageName());
	}

	private static void copyRecursively(File src, File dest)
			throws IOException {
		if (src.isDirectory()) {
			if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException();
			File[] children = src.listFiles();
			if (children == null) throw new IOException();
			for (File child : children) {
				copyRecursively(child, new File(dest, child.getName()));
			}
		} else if (src.isFile()) {
			InputStream in = new FileInputStream(src);
			OutputStream out = new FileOutputStream(dest);
			copyAndClose(in, out);
		}
	}
}
