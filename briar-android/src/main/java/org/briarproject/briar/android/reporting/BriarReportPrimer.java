package org.briarproject.briar.android.reporting;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import org.acra.builder.ReportBuilder;
import org.acra.builder.ReportPrimer;
import org.briarproject.bramble.api.Pair;
import org.briarproject.briar.BuildConfig;
import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.android.logging.BriefLogFormatter;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import androidx.annotation.NonNull;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.WIFI_P2P_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.Collections.unmodifiableMap;
import static org.briarproject.bramble.util.AndroidUtils.getBluetoothAddressAndMethod;
import static org.briarproject.bramble.util.PrivacyUtils.scrubInetAddress;
import static org.briarproject.bramble.util.PrivacyUtils.scrubMacAddress;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

public class BriarReportPrimer implements ReportPrimer {

	@Override
	public void primeReport(@NonNull Context ctx,
			@NonNull ReportBuilder builder) {
		CustomDataTask task = new CustomDataTask(ctx);
		FutureTask<Map<String, String>> futureTask = new FutureTask<>(task);
		// Use a new thread as the Android executor thread may have died
		new SingleShotAndroidExecutor(futureTask).start();
		try {
			builder.customData(futureTask.get());
		} catch (InterruptedException | ExecutionException e) {
			builder.customData("Custom data exception", e.toString());
		}
	}

	private static class CustomDataTask
			implements Callable<Map<String, String>> {

		private final Context ctx;

		private CustomDataTask(Context ctx) {
			this.ctx = ctx;
		}

		@Override
		public Map<String, String> call() {
			Map<String, String> customData = new LinkedHashMap<>();

			// Log
			BriarApplication app =
					(BriarApplication) ctx.getApplicationContext();
			StringBuilder sb = new StringBuilder();
			Formatter formatter = new BriefLogFormatter();
			for (LogRecord record : app.getRecentLogRecords()) {
				sb.append(formatter.format(record)).append('\n');
			}
			customData.put("Log", sb.toString());

			// System memory
			Object o = ctx.getSystemService(ACTIVITY_SERVICE);
			ActivityManager am = (ActivityManager) o;
			ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
			am.getMemoryInfo(mem);
			String systemMemory;
			systemMemory = (mem.totalMem / 1024 / 1024) + " MiB total, "
					+ (mem.availMem / 1024 / 1204) + " MiB free, "
					+ (mem.threshold / 1024 / 1024) + " MiB threshold";
			customData.put("System memory", systemMemory);

			// Virtual machine memory
			Runtime runtime = Runtime.getRuntime();
			long heap = runtime.totalMemory();
			long heapFree = runtime.freeMemory();
			long heapMax = runtime.maxMemory();
			String vmMemory = (heap / 1024 / 1024) + " MiB allocated, "
					+ (heapFree / 1024 / 1024) + " MiB free, "
					+ (heapMax / 1024 / 1024) + " MiB maximum";
			customData.put("Virtual machine memory", vmMemory);

			// Internal storage
			File root = Environment.getRootDirectory();
			long rootTotal = root.getTotalSpace();
			long rootFree = root.getFreeSpace();
			String internal = (rootTotal / 1024 / 1024) + " MiB total, "
					+ (rootFree / 1024 / 1024) + " MiB free";
			customData.put("Internal storage", internal);

			// External storage (SD card)
			File sd = Environment.getExternalStorageDirectory();
			long sdTotal = sd.getTotalSpace();
			long sdFree = sd.getFreeSpace();
			String external = (sdTotal / 1024 / 1024) + " MiB total, "
					+ (sdFree / 1024 / 1024) + " MiB free";
			customData.put("External storage", external);

			// Is mobile data available?
			o = ctx.getSystemService(CONNECTIVITY_SERVICE);
			ConnectivityManager cm = (ConnectivityManager) o;
			NetworkInfo mobile = cm.getNetworkInfo(TYPE_MOBILE);
			boolean mobileAvailable = mobile != null && mobile.isAvailable();
			// Is mobile data enabled?
			boolean mobileEnabled = false;
			try {
				Class<?> clazz = Class.forName(cm.getClass().getName());
				Method method = clazz.getDeclaredMethod("getMobileDataEnabled");
				method.setAccessible(true);
				mobileEnabled = (Boolean) method.invoke(cm);
			} catch (ClassNotFoundException
					| NoSuchMethodException
					| IllegalArgumentException
					| InvocationTargetException
					| IllegalAccessException e) {
				customData.put("Mobile data reflection exception",
						e.toString());
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
			customData.put("Mobile data status", mobileStatus);

			// Is wifi available?
			NetworkInfo wifi = cm.getNetworkInfo(TYPE_WIFI);
			boolean wifiAvailable = wifi != null && wifi.isAvailable();
			// Is wifi enabled?
			o = ctx.getApplicationContext().getSystemService(WIFI_SERVICE);
			WifiManager wm = (WifiManager) o;
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
			customData.put("Wi-Fi status", wifiStatus);

			// Is wifi direct supported?
			String wifiDirectStatus = "Supported";
			if (ctx.getSystemService(WIFI_P2P_SERVICE) == null)
				wifiDirectStatus = "Not supported";
			customData.put("Wi-Fi Direct", wifiDirectStatus);

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
						customData.put("Wi-Fi address",
								scrubInetAddress(address));
					} catch (UnknownHostException ignored) {
						// Should only be thrown if address has illegal length
					}
				}
			}

			// Is Bluetooth available?
			BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
			if (bt == null) {
				customData.put("Bluetooth status", "Not available");
			} else {
				// Is Bluetooth enabled?
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
				customData.put("Bluetooth status", btStatus);

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
					customData.put("Bluetooth LE status", btLeStatus);
				}

				Pair<String, String> p = getBluetoothAddressAndMethod(ctx, bt);
				String address = p.getFirst();
				String method = p.getSecond();
				customData.put("Bluetooth address", scrubMacAddress(address));
				customData.put("Bluetooth address method", method);
			}

			// Git commit ID
			customData.put("Commit ID", BuildConfig.GitHash);

			return unmodifiableMap(customData);
		}
	}

	private static class SingleShotAndroidExecutor extends Thread {

		private final Runnable runnable;

		private SingleShotAndroidExecutor(Runnable runnable) {
			this.runnable = runnable;
		}

		@Override
		public void run() {
			Looper.prepare();
			Handler handler = new Handler();
			handler.post(runnable);
			handler.post(() -> {
				Looper looper = Looper.myLooper();
				if (looper != null) looper.quit();
			});
			Looper.loop();
		}
	}
}
