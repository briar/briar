package org.briarproject.bramble.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import static android.content.Context.MODE_PRIVATE;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.Arrays.asList;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;

@NotNullByDefault
public class AndroidUtils {

	// Fake Bluetooth address returned by BluetoothAdapter on API 23 and later
	private static final String FAKE_BLUETOOTH_ADDRESS = "02:00:00:00:00:00";

	private static final String STORED_REPORTS = "dev-reports";
	private static final String STORED_LOGCAT = "dev-logcat";

	public static Collection<String> getSupportedArchitectures() {
		List<String> abis = new ArrayList<>();
		if (SDK_INT >= 21) {
			abis.addAll(asList(Build.SUPPORTED_ABIS));
		} else {
			abis.add(Build.CPU_ABI);
			if (Build.CPU_ABI2 != null) abis.add(Build.CPU_ABI2);
		}
		return abis;
	}

	public static String getBluetoothAddress(Context ctx,
			BluetoothAdapter adapter) {
		return getBluetoothAddressAndMethod(ctx, adapter).getFirst();
	}

	public static Pair<String, String> getBluetoothAddressAndMethod(Context ctx,
			BluetoothAdapter adapter) {
		// Return the adapter's address if it's valid and not fake
		@SuppressLint("HardwareIds")
		String address = adapter.getAddress();
		if (isValidBluetoothAddress(address)) {
			return new Pair<>(address, "adapter");
		}
		// Return the address from settings if it's valid and not fake
		address = Settings.Secure.getString(ctx.getContentResolver(),
				"bluetooth_address");
		if (isValidBluetoothAddress(address)) {
			return new Pair<>(address, "settings");
		}
		// Try to get the address via reflection
		address = getBluetoothAddressByReflection(adapter);
		if (isValidBluetoothAddress(address)) {
			return new Pair<>(requireNonNull(address), "reflection");
		}
		// Let the caller know we can't find the address
		return new Pair<>("", "");
	}

	public static boolean isValidBluetoothAddress(@Nullable String address) {
		return !StringUtils.isNullOrEmpty(address)
				&& BluetoothAdapter.checkBluetoothAddress(address)
				&& !address.equals(FAKE_BLUETOOTH_ADDRESS);
	}

	@Nullable
	private static String getBluetoothAddressByReflection(
			BluetoothAdapter adapter) {
		try {
			Field mServiceField =
					adapter.getClass().getDeclaredField("mService");
			mServiceField.setAccessible(true);
			Object mService = mServiceField.get(adapter);
			// mService may be null when Bluetooth is disabled
			if (mService == null) throw new NoSuchFieldException();
			Method getAddressMethod =
					mService.getClass().getMethod("getAddress");
			return (String) getAddressMethod.invoke(mService);
		} catch (NoSuchFieldException e) {
			return null;
		} catch (IllegalAccessException e) {
			return null;
		} catch (NoSuchMethodException e) {
			return null;
		} catch (InvocationTargetException e) {
			return null;
		} catch (SecurityException e) {
			return null;
		}
	}

	public static File getReportDir(Context ctx) {
		return ctx.getDir(STORED_REPORTS, MODE_PRIVATE);
	}

	public static File getLogcatFile(Context ctx) {
		return new File(ctx.getFilesDir(), STORED_LOGCAT);
	}

	/**
	 * Returns an array of supported content types for image attachments.
	 */
	public static String[] getSupportedImageContentTypes() {
		return new String[] {"image/jpeg", "image/png", "image/gif"};
	}
}
