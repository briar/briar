package org.briarproject.bramble.util;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

public class AndroidUtils {

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

	public static void deleteAppData(Context ctx) {
		File dataDir = new File(ctx.getApplicationInfo().dataDir);
		File[] children = dataDir.listFiles();
		if (children != null) {
			for (File child : children) {
				if (!child.getName().equals("lib"))
					IoUtils.deleteFileOrDir(child);
			}
		}
		// Recreate the cache dir as some OpenGL drivers expect it to exist
		new File(dataDir, "cache").mkdir();
	}

	public static File getReportDir(Context ctx) {
		return ctx.getDir(STORED_REPORTS, MODE_PRIVATE);
	}
}
