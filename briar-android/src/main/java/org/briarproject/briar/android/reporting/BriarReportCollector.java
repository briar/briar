/*
  Some of the code in this file was copied from or inspired by ACRA
  which is licenced under Apache 2.0 and authored by F43nd1r.
  https://github.com/ACRA/acra/blob/3b9034/acra-core/src/main/java/org/acra/collector/
 */

package org.briarproject.briar.android.reporting;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;

import org.briarproject.bramble.api.Pair;
import org.briarproject.briar.BuildConfig;
import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.android.logging.BriefLogFormatter;
import org.briarproject.briar.android.reporting.ReportData.MultiReportInfo;
import org.briarproject.briar.android.reporting.ReportData.ReportItem;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import androidx.annotation.Nullable;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.content.Context.WIFI_P2P_SERVICE;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.content.ContextCompat.getSystemService;
import static java.util.Objects.requireNonNull;
import static org.briarproject.bramble.util.AndroidUtils.getBluetoothAddressAndMethod;
import static org.briarproject.bramble.util.PrivacyUtils.scrubInetAddress;
import static org.briarproject.bramble.util.PrivacyUtils.scrubMacAddress;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

class BriarReportCollector {

	private final Context ctx;

	BriarReportCollector(Context ctx) {
		this.ctx = ctx;
	}

	public ReportData collectReportData(@Nullable Throwable t,
			long appStartTime) {
		return new ReportData()
				.add(getBasicInfo(t))
				.add(getDeviceInfo())
				.add(getTimeInfo(appStartTime))
				.add(getMemory())
				.add(getStorage())
				.add(getConnectivity())
				.add(getBuildConfig())
				.add(getLogcat())
				.add(getDeviceFeatures());
	}

	private ReportItem getBasicInfo(@Nullable Throwable t) {
		String packageName = ctx.getPackageName();
		PackageManager pm = ctx.getPackageManager();
		String versionName, versionCode;
		try {
			PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
			versionName = packageInfo.versionName;
			versionCode = String.valueOf(packageInfo.versionCode);
		} catch (PackageManager.NameNotFoundException e) {
			versionName = e.toString();
			versionCode = "?";
		}
		MultiReportInfo basicInfo = new MultiReportInfo()
				.add("Package name", packageName)
				.add("Version name", versionName)
				.add("Version code", versionCode);
		// print stacktrace of Throwable if this is not feedback
		if (t != null) {
			final Writer sw = new StringWriter();
			final PrintWriter printWriter = new PrintWriter(sw);
			if (!isNullOrEmpty(t.getMessage())) {
				printWriter.println(t.getMessage());
			}
			t.printStackTrace(printWriter);
			basicInfo.add("stracktrace", sw.toString());
		}
		return new ReportItem("BasicInfo", R.string.dev_report_basic_info,
				basicInfo, false);
	}

	private ReportItem getDeviceInfo() {
		MultiReportInfo deviceInfo = new MultiReportInfo()
				.add("Android version", Build.VERSION.RELEASE)
				.add("Android SDK API", String.valueOf(SDK_INT))
				.add("Product", Build.PRODUCT)
				.add("Model", Build.MODEL)
				.add("Brand", Build.BRAND);
		return new ReportItem("DeviceInfo", R.string.dev_report_device_info,
				deviceInfo);
	}

	private ReportItem getTimeInfo(long startTime) {
		MultiReportInfo timeInfo = new MultiReportInfo()
				.add("App start time", formatTime(startTime))
				.add("Crash time", formatTime(System.currentTimeMillis()));
		return new ReportItem("DeviceInfo", R.string.dev_report_time_info,
				timeInfo);
	}

	private String formatTime(long time) {
		return new Date(time) + " (" + time + ")";
	}

	private ReportItem getMemory() {
		// System memory
		ActivityManager am = getSystemService(ctx, ActivityManager.class);
		ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
		requireNonNull(am).getMemoryInfo(mem);
		String systemMemory;
		systemMemory = (mem.totalMem / 1024 / 1024) + " MiB total, "
				+ (mem.availMem / 1024 / 1204) + " MiB free, "
				+ (mem.threshold / 1024 / 1024) + " MiB threshold";

		// Virtual machine memory
		Runtime runtime = Runtime.getRuntime();
		long heap = runtime.totalMemory();
		long heapFree = runtime.freeMemory();
		long heapMax = runtime.maxMemory();
		String vmMemory = (heap / 1024 / 1024) + " MiB allocated, "
				+ (heapFree / 1024 / 1024) + " MiB free, "
				+ (heapMax / 1024 / 1024) + " MiB maximum";

		MultiReportInfo memInfo = new MultiReportInfo()
				.add("System memory", systemMemory)
				.add("Virtual machine memory", vmMemory);
		return new ReportItem("Memory", R.string.dev_report_memory, memInfo);
	}

	private ReportItem getStorage() {
		// Internal storage
		File root = Environment.getRootDirectory();
		long rootTotal = root.getTotalSpace();
		long rootFree = root.getFreeSpace();
		String internal = (rootTotal / 1024 / 1024) + " MiB total, "
				+ (rootFree / 1024 / 1024) + " MiB free";

		// External storage (SD card)
		File sd = Environment.getExternalStorageDirectory();
		long sdTotal = sd.getTotalSpace();
		long sdFree = sd.getFreeSpace();
		String external = (sdTotal / 1024 / 1024) + " MiB total, "
				+ (sdFree / 1024 / 1024) + " MiB free";

		MultiReportInfo storageInfo = new MultiReportInfo()
				.add("Internal storage", internal)
				.add("External storage", external);
		return new ReportItem("Storage", R.string.dev_report_storage,
				storageInfo);
	}


	private ReportItem getConnectivity() {
		MultiReportInfo connectivityInfo = new MultiReportInfo();

		// Is mobile data available?
		ConnectivityManager cm = requireNonNull(
				getSystemService(ctx, ConnectivityManager.class));
		NetworkInfo mobile = cm.getNetworkInfo(TYPE_MOBILE);
		boolean mobileAvailable = mobile != null && mobile.isAvailable();
		// Is mobile data enabled?
		boolean mobileEnabled = false;
		try {
			Class<?> clazz = Class.forName(cm.getClass().getName());
			Method method = clazz.getDeclaredMethod("getMobileDataEnabled");
			method.setAccessible(true);
			//noinspection ConstantConditions
			mobileEnabled = (Boolean) method.invoke(cm);
		} catch (ClassNotFoundException
				| NoSuchMethodException
				| IllegalArgumentException
				| InvocationTargetException
				| IllegalAccessException e) {
			connectivityInfo
					.add("Mobile data reflection exception", e.toString());
		}
		// Is mobile data connected ?
		boolean mobileConnected = mobile != null && mobile.isConnected();

		String mobileStatus;
		if (mobileAvailable) mobileStatus = "Available, ";
		else mobileStatus = "Not available, ";
		if (mobileEnabled) mobileStatus += "enabled, ";
		else mobileStatus += "not enabled, ";
		if (mobileConnected) mobileStatus += "connected";
		else mobileStatus += "not connected";
		connectivityInfo.add("Mobile data status", mobileStatus);

		// Is wifi available?
		NetworkInfo wifi = cm.getNetworkInfo(TYPE_WIFI);
		boolean wifiAvailable = wifi != null && wifi.isAvailable();
		// Is wifi enabled?
		WifiManager wm = getSystemService(ctx, WifiManager.class);
		boolean wifiEnabled = wm != null &&
				wm.getWifiState() == WIFI_STATE_ENABLED;
		// Is wifi connected?
		boolean wifiConnected = wifi != null && wifi.isConnected();

		String wifiStatus;
		if (wifiAvailable) wifiStatus = "Available, ";
		else wifiStatus = "Not available, ";
		if (wifiEnabled) wifiStatus += "enabled, ";
		else wifiStatus += "not enabled, ";
		if (wifiConnected) wifiStatus += "connected";
		else wifiStatus += "not connected";
		connectivityInfo.add("Wi-Fi status", wifiStatus);

		// Is wifi direct supported?
		String wifiDirectStatus = "Supported";
		if (ctx.getSystemService(WIFI_P2P_SERVICE) == null)
			wifiDirectStatus = "Not supported";
		connectivityInfo.add("Wi-Fi Direct", wifiDirectStatus);

		if (wm != null) {
			WifiInfo wifiInfo = wm.getConnectionInfo();
			if (wifiInfo != null) {
				int ip = wifiInfo.getIpAddress(); // Nice API, Google
				byte[] ipBytes = new byte[4];
				ipBytes[0] = (byte) (ip & 0xFF);
				ipBytes[1] = (byte) ((ip >> 8) & 0xFF);
				ipBytes[2] = (byte) ((ip >> 16) & 0xFF);
				ipBytes[3] = (byte) ((ip >> 24) & 0xFF);
				try {
					InetAddress address = InetAddress.getByAddress(ipBytes);
					connectivityInfo
							.add("Wi-Fi address", scrubInetAddress(address));
				} catch (UnknownHostException ignored) {
					// Should only be thrown if address has illegal length
				}
			}
		}

		// Is Bluetooth available?
		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
		if (bt == null) {
			connectivityInfo.add("Bluetooth status", "Not available");
		} else {
			// Is Bluetooth enabled?
			@SuppressLint("HardwareIds")
			boolean btEnabled = bt.isEnabled()
					&& !isNullOrEmpty(bt.getAddress());
			// Is Bluetooth connectable?
			int scanMode = bt.getScanMode();
			boolean btConnectable = scanMode == SCAN_MODE_CONNECTABLE ||
					scanMode == SCAN_MODE_CONNECTABLE_DISCOVERABLE;
			// Is Bluetooth discoverable?
			boolean btDiscoverable =
					scanMode == SCAN_MODE_CONNECTABLE_DISCOVERABLE;

			String btStatus;
			if (btEnabled) btStatus = "Available, enabled, ";
			else btStatus = "Available, not enabled, ";
			if (btConnectable) btStatus += "connectable, ";
			else btStatus += "not connectable, ";
			if (btDiscoverable) btStatus += "discoverable";
			else btStatus += "not discoverable";
			connectivityInfo.add("Bluetooth status", btStatus);

			if (SDK_INT >= 21) {
				// Is Bluetooth LE scanning and advertising supported?
				boolean btLeScan = bt.getBluetoothLeScanner() != null;
				boolean btLeAdvertise =
						bt.getBluetoothLeAdvertiser() != null;
				String btLeStatus;
				if (btLeScan) btLeStatus = "Scanning, ";
				else btLeStatus = "No scanning, ";
				if (btLeAdvertise) btLeStatus += "advertising";
				else btLeStatus += "no advertising";
				connectivityInfo.add("Bluetooth LE status", btLeStatus);
			}

			Pair<String, String> p = getBluetoothAddressAndMethod(ctx, bt);
			String address = p.getFirst();
			String method = p.getSecond();
			connectivityInfo.add("Bluetooth address", scrubMacAddress(address));
			connectivityInfo.add("Bluetooth address method", method);
		}
		return new ReportItem("Connectivity", R.string.dev_report_connectivity,
				connectivityInfo);
	}

	private ReportItem getBuildConfig() {
		MultiReportInfo buildConfig = new MultiReportInfo()
				.add("GitHash", BuildConfig.GitHash)
				.add("BUILD_TYPE", BuildConfig.BUILD_TYPE)
				.add("FLAVOR", BuildConfig.FLAVOR)
				.add("DEBUG", String.valueOf(BuildConfig.DEBUG))
				.add("BuildTimestamp",
						String.valueOf(BuildConfig.BuildTimestamp));
		return new ReportItem("BuildConfig", R.string.dev_report_build_config,
				buildConfig);
	}

	private ReportItem getLogcat() {
		BriarApplication app = (BriarApplication) ctx.getApplicationContext();
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new BriefLogFormatter();
		for (LogRecord record : app.getRecentLogRecords()) {
			sb.append(formatter.format(record)).append('\n');
		}
		return new ReportItem("Logcat", R.string.dev_report_logcat,
				sb.toString());
	}

	private ReportItem getDeviceFeatures() {
		PackageManager pm = ctx.getPackageManager();
		FeatureInfo[] features = pm.getSystemAvailableFeatures();
		MultiReportInfo deviceFeatures = new MultiReportInfo();
		for (FeatureInfo feature : features) {
			String featureName = feature.name;
			if (featureName != null) {
				deviceFeatures.add(featureName, "true");
			} else {
				deviceFeatures.add("glEsVersion", feature.getGlEsVersion());
			}
		}
		return new ReportItem("DeviceFeatures",
				R.string.dev_report_device_features, deviceFeatures);
	}

}
