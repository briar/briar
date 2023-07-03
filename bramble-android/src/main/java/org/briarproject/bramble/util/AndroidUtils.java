package org.briarproject.bramble.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;

import org.briarproject.bramble.api.Pair;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

import javax.annotation.Nullable;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Context.MODE_PRIVATE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Process.myPid;
import static android.os.Process.myUid;
import static java.util.Arrays.asList;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;

@NotNullByDefault
public class AndroidUtils {

	// Fake Bluetooth address returned by BluetoothAdapter on API 23 and later
	private static final String FAKE_BLUETOOTH_ADDRESS = "02:00:00:00:00:00";

	private static final String STORED_REPORTS = "dev-reports";
	private static final String STORED_LOGCAT = "dev-logcat";

	public static Collection<String> getSupportedArchitectures() {
		return asList(Build.SUPPORTED_ABIS);
	}

	public static boolean hasBtConnectPermission(Context ctx) {
		return SDK_INT < 31 || ctx.checkPermission(BLUETOOTH_CONNECT, myPid(),
				myUid()) == PERMISSION_GRANTED;
	}

	public static String getBluetoothAddress(Context ctx,
			BluetoothAdapter adapter) {
		return getBluetoothAddressAndMethod(ctx, adapter).getFirst();
	}

	public static Pair<String, String> getBluetoothAddressAndMethod(Context ctx,
			BluetoothAdapter adapter) {
		// If we don't have permission to access the adapter's address, let
		// the caller know we can't find it
		if (!hasBtConnectPermission(ctx)) return new Pair<>("", "");
		// Return the adapter's address if it's valid and not fake
		@SuppressLint("HardwareIds")
		String address = adapter.getAddress();
		if (isValidBluetoothAddress(address)) {
			return new Pair<>(address, "adapter");
		}
		// Return the address from settings if it's valid and not fake
		if (SDK_INT < 33) {
			address = Settings.Secure.getString(ctx.getContentResolver(),
					"bluetooth_address");
			if (isValidBluetoothAddress(address)) {
				return new Pair<>(address, "settings");
			}
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

	public static boolean isUiThread() {
		return Looper.myLooper() == Looper.getMainLooper();
	}

	public static int getImmutableFlags(int flags) {
		if (SDK_INT >= 23) {
			return FLAG_IMMUTABLE | flags;
		}
		return flags;
	}
}
