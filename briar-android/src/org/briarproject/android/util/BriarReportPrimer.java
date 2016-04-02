package org.briarproject.android.util;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import org.acra.builder.ReportBuilder;
import org.acra.builder.ReportPrimer;
import org.briarproject.util.StringUtils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static java.util.logging.Level.WARNING;

public class BriarReportPrimer implements ReportPrimer {

	private static final Logger LOG =
			Logger.getLogger(BriarReportPrimer.class.getName());

	@Override
	public void primeReport(Context context, ReportBuilder builder) {
		// System memory
		Object o = context.getSystemService(ACTIVITY_SERVICE);
		ActivityManager am = (ActivityManager) o;
		ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
		am.getMemoryInfo(mem);
		String systemMemory;
		if (Build.VERSION.SDK_INT >= 16) {
			systemMemory = (mem.totalMem / 1024 / 1024) + " MiB total, "
					+ (mem.availMem / 1024 / 1204) + " MiB free, "
					+ (mem.threshold / 1024 / 1024) + " MiB threshold";
		} else {
			systemMemory = (mem.availMem / 1024 / 1204) + " MiB free, "
					+ (mem.threshold / 1024 / 1024) + " MiB threshold";
		}
		builder.customData("System memory", systemMemory);

		// Virtual machine memory
		Runtime runtime = Runtime.getRuntime();
		long heap = runtime.totalMemory();
		long heapFree = runtime.freeMemory();
		long heapMax = runtime.maxMemory();
		String vmMemory = (heap / 1024 / 1024) + " MiB allocated, "
				+ (heapFree / 1024 / 1024) + " MiB free, "
				+ (heapMax / 1024 / 1024) + " MiB maximum";
		builder.customData("Virtual machine memory", vmMemory);

		// Internal storage
		File root = Environment.getRootDirectory();
		long rootTotal = root.getTotalSpace();
		long rootFree = root.getFreeSpace();
		String internal = (rootTotal / 1024 / 1024) + " MiB total, "
				+ (rootFree / 1024 / 1024) + " MiB free";
		builder.customData("Internal storage", internal);

		// External storage (SD card)
		File sd = Environment.getExternalStorageDirectory();
		long sdTotal = sd.getTotalSpace();
		long sdFree = sd.getFreeSpace();
		String external = (sdTotal / 1024 / 1024) + " MiB total, "
				+ (sdFree / 1024 / 1024) + " MiB free";
		builder.customData("External storage", external);

		// Is mobile data available?
		o = context.getSystemService(CONNECTIVITY_SERVICE);
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
		} catch (ClassNotFoundException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (NoSuchMethodException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (IllegalAccessException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (IllegalArgumentException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (InvocationTargetException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
		builder.customData("Mobile data status", mobileStatus);

		// Is wifi available?
		NetworkInfo wifi = cm.getNetworkInfo(TYPE_WIFI);
		boolean wifiAvailable = wifi != null && wifi.isAvailable();
		// Is wifi enabled?
		WifiManager wm = (WifiManager) context.getSystemService(WIFI_SERVICE);
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
		builder.customData("Wi-Fi status", wifiStatus);

		if (wm != null) {
			WifiInfo wifiInfo = wm.getConnectionInfo();
			if (wifiInfo != null) {
				int ip = wifiInfo.getIpAddress(); // Nice API, Google
				int ip1 = ip & 0xFF;
				int ip2 = (ip >> 8) & 0xFF;
				int ip3 = (ip >> 16) & 0xFF;
				int ip4 = (ip >> 24) & 0xFF;
				String address = ip1 + "." + ip2 + "." + ip3 + "." + ip4;
				builder.customData("Wi-Fi address", address);
			}
		}

		// Is Bluetooth available?
		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
		boolean btAvailable = bt != null;
		// Is Bluetooth enabled?
		boolean btEnabled = bt != null && bt.isEnabled() &&
				!StringUtils.isNullOrEmpty(bt.getAddress());
		// Is Bluetooth connectable?
		boolean btConnectable = bt != null &&
				(bt.getScanMode() == SCAN_MODE_CONNECTABLE ||
						bt.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE);
		// Is Bluetooth discoverable?
		boolean btDiscoverable = bt != null &&
				bt.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE;

		String btStatus;
		if (btAvailable) btStatus = "Available, ";
		else btStatus = "Not available, ";
		if (btEnabled) btStatus += "enabled, ";
		else btStatus += "not enabled, ";
		if (btConnectable) btStatus += "connectable, ";
		else btStatus += "not connectable, ";
		if (btDiscoverable) btStatus += "discoverable";
		else btStatus += "not discoverable";
		builder.customData("Bluetooth status", btStatus);

		if (bt != null) builder.customData("Bluetooth address", bt.getAddress());
		String btSettingsAddr;
		try {
			btSettingsAddr = Settings.Secure.getString(context.getContentResolver(),
					"bluetooth_address");
		} catch (SecurityException e) {
			btSettingsAddr = "Could not get address from settings";
		}
		builder.customData("Bluetooth address from settings", btSettingsAddr);
	}
}
