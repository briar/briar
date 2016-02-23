package org.briarproject.android.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.support.design.widget.TextInputLayout;

import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidUtils {

	// Fake Bluetooth address returned by BluetoothAdapter on API 23 and later
	private static final String FAKE_BLUETOOTH_ADDRESS = "02:00:00:00:00:00";

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

	public static void enableBluetooth(final BluetoothAdapter adapter,
			final boolean enable) {
		new Thread() {
			@Override
			public void run() {
				if (enable) adapter.enable();
				else adapter.disable();
			}
		}.start();
	}

	public static String getBluetoothAddress(Context ctx,
			BluetoothAdapter adapter) {
		// Return the adapter's address if it's valid and not fake
		String address = adapter.getAddress();
		if (!StringUtils.isNullOrEmpty(address)
				&& BluetoothAdapter.checkBluetoothAddress(address)
				&& !address.equals(FAKE_BLUETOOTH_ADDRESS)) {
			return address;
		}
		// Return the address from settings if it's valid
		address = Settings.Secure.getString(ctx.getContentResolver(),
				"bluetooth_address");
		if (!StringUtils.isNullOrEmpty(address)
				&& BluetoothAdapter.checkBluetoothAddress(address)) {
			return address;
		}
		// As a last resort, return a fake but valid address
		return FAKE_BLUETOOTH_ADDRESS;
	}
}
