package org.briarproject.bramble.util;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

import static android.content.Context.MODE_PRIVATE;
import static java.util.logging.Level.INFO;

public class AndroidUtils {

	private static final Logger LOG =
			Logger.getLogger(AndroidUtils.class.getName());

	// Fake Bluetooth address returned by BluetoothAdapter on API 23 and later
	private static final String FAKE_BLUETOOTH_ADDRESS = "02:00:00:00:00:00";

	private static final String STORED_REPORTS = "dev-reports";

	@SuppressWarnings("deprecation")
	public static Collection<String> getSupportedArchitectures() {
		List<String> abis = new ArrayList<>();
		if (Build.VERSION.SDK_INT >= 21) {
			abis.addAll(Arrays.asList(Build.SUPPORTED_ABIS));
		} else {
			abis.add(Build.CPU_ABI);
			if (Build.CPU_ABI2 != null) abis.add(Build.CPU_ABI2);
		}
		return abis;
	}

	public static String getBluetoothAddress(Context ctx,
			BluetoothAdapter adapter) {
		// Return the adapter's address if it's valid and not fake
		String address = adapter.getAddress();
		if (isValidBluetoothAddress(address)) return address;
		// Return the address from settings if it's valid and not fake
		address = Settings.Secure.getString(ctx.getContentResolver(),
				"bluetooth_address");
		if (isValidBluetoothAddress(address)) return address;
		// Let the caller know we can't find the address
		return "";
	}

	private static boolean isValidBluetoothAddress(String address) {
		return !StringUtils.isNullOrEmpty(address)
				&& BluetoothAdapter.checkBluetoothAddress(address)
				&& !address.equals(FAKE_BLUETOOTH_ADDRESS);
	}

	public static void logDataDirContents(Context ctx) {
		if (LOG.isLoggable(INFO)) {
			LOG.info("Contents of data directory:");
			logFileOrDir(new File(ctx.getApplicationInfo().dataDir));
		}
	}

	private static void logFileOrDir(File f) {
		LOG.info(f.getAbsolutePath() + " " + f.length());
		if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children == null) {
				LOG.info("Could not list files in " + f.getAbsolutePath());
			} else {
				for (File child : children) logFileOrDir(child);
			}
		}
	}

	public static File getSharedPrefsFile(Context ctx, String name) {
		File dataDir = new File(ctx.getApplicationInfo().dataDir);
		File prefsDir = new File(dataDir, "shared_prefs");
		return new File(prefsDir, name + ".xml");
	}

	public static void logFileContents(File f) {
		if (LOG.isLoggable(INFO)) {
			LOG.info("Contents of " + f.getAbsolutePath() + ":");
			try {
				Scanner s = new Scanner(f);
				while (s.hasNextLine()) LOG.info(s.nextLine());
				s.close();
			} catch (FileNotFoundException e) {
				LOG.info(f.getAbsolutePath() + " not found");
			}
		}
	}

	public static void deleteAppData(Context ctx) {
		File dataDir = new File(ctx.getApplicationInfo().dataDir);
		if (LOG.isLoggable(INFO))
			LOG.info("Deleting app data from " + dataDir.getAbsolutePath());
		File[] children = dataDir.listFiles();
		if (children != null) {
			for (File child : children) {
				if (!child.getName().equals("lib")) {
					if (LOG.isLoggable(INFO))
						LOG.info("Deleting " + child.getAbsolutePath());
					IoUtils.deleteFileOrDir(child);
				}
			}
		} else if (LOG.isLoggable(INFO)) {
			LOG.info("Could not list files in " + dataDir.getAbsolutePath());
		}
		// Recreate the cache dir as some OpenGL drivers expect it to exist
		boolean recreated = new File(dataDir, "cache").mkdir();
		if (LOG.isLoggable(INFO)) {
			if (recreated) LOG.info("Recreated cache dir");
			else LOG.info("Could not recreate cache dir");
		}
	}

	public static File getReportDir(Context ctx) {
		return ctx.getDir(STORED_REPORTS, MODE_PRIVATE);
	}
}
