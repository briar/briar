package org.briarproject.android.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.support.design.widget.TextInputLayout;
import android.text.format.DateUtils;

import org.briarproject.util.FileUtils;
import org.briarproject.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
import static android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE;
import static android.text.format.DateUtils.FORMAT_ABBREV_TIME;
import static android.text.format.DateUtils.FORMAT_SHOW_DATE;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.WEEK_IN_MILLIS;

public class AndroidUtils {

	// Fake Bluetooth address returned by BluetoothAdapter on API 23 and later
	private static final String FAKE_BLUETOOTH_ADDRESS = "02:00:00:00:00:00";

	private static final String STORED_REPORTS = "dev-reports";

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public static Collection<String> getSupportedArchitectures() {
		List<String> abis = new ArrayList<String>();
		if (Build.VERSION.SDK_INT >= 21) {
			abis.addAll(Arrays.asList(Build.SUPPORTED_ABIS));
		} else {
			abis.add(Build.CPU_ABI);
			if (Build.CPU_ABI2 != null) abis.add(Build.CPU_ABI2);
		}
		return Collections.unmodifiableList(abis);
	}

	public static void setError(TextInputLayout til, String error,
			boolean condition) {
		if (condition) {
			if (til.getError() == null)
				til.setError(error);
		} else
			til.setError(null);
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
					FileUtils.deleteFileOrDir(child);
			}
		}
	}

	public static File getReportDir(Context ctx) {
		return ctx.getDir(STORED_REPORTS, MODE_PRIVATE);
	}

	public static String formatDate(Context ctx, long time) {
		long minResolution = MINUTE_IN_MILLIS;
		int flags = FORMAT_ABBREV_RELATIVE |
				FORMAT_SHOW_DATE | FORMAT_ABBREV_TIME | FORMAT_ABBREV_MONTH;

		// also show time when older than a day, but newer than a week
		long diff = System.currentTimeMillis() - time;
		if (diff >= DAY_IN_MILLIS && diff < WEEK_IN_MILLIS) {
			return DateUtils.getRelativeDateTimeString(ctx, time, minResolution,
					WEEK_IN_MILLIS, flags).toString();
		}
		// otherwise just show "...ago" or date string
		return DateUtils
				.getRelativeTimeSpanString(time, System.currentTimeMillis(),
						minResolution, flags).toString();
	}

}
