package org.briarproject.bramble.util;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.WIFI_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static java.net.NetworkInterface.getNetworkInterfaces;
import static java.util.Collections.list;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.StringUtils.ipToString;
import static org.briarproject.bramble.util.StringUtils.toHexString;

@SuppressLint("HardwareIds")
public class AndroidUtils {

	// Fake Bluetooth address returned by BluetoothAdapter on API 23 and later
	private static final String FAKE_BLUETOOTH_ADDRESS = "02:00:00:00:00:00";

	private static final String STORED_REPORTS = "dev-reports";

	@SuppressWarnings("deprecation")
	public static Collection<String> getSupportedArchitectures() {
		List<String> abis = new ArrayList<>();
		if (SDK_INT >= 21) {
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

	public static void logNetworkState(Context ctx, Logger logger) {
		if (!logger.isLoggable(INFO)) return;

		Object o = ctx.getSystemService(CONNECTIVITY_SERVICE);
		if (o == null) throw new AssertionError();
		ConnectivityManager cm = (ConnectivityManager) o;
		o = ctx.getApplicationContext().getSystemService(WIFI_SERVICE);
		if (o == null) throw new AssertionError();
		WifiManager wm = (WifiManager) o;

		StringBuilder s = new StringBuilder();
		logWifiInfo(s, wm.getConnectionInfo());
		logNetworkInfo(s, cm.getActiveNetworkInfo(), true);
		if (SDK_INT >= 21) {
			for (Network network : cm.getAllNetworks())
				logNetworkInfo(s, cm.getNetworkInfo(network), false);
		} else {
			for (NetworkInfo info : cm.getAllNetworkInfo())
				logNetworkInfo(s, info, false);
		}
		try {
			for (NetworkInterface iface : list(getNetworkInterfaces()))
				logNetworkInterface(s, iface);
		} catch (SocketException e) {
			logger.log(WARNING, e.toString(), e);
		}
		logger.log(INFO, s.toString());
	}

	private static void logWifiInfo(StringBuilder s, @Nullable WifiInfo info) {
		if (info == null) {
			s.append("Wifi info: null\n");
			return;
		}
		s.append("Wifi info:\n");
		s.append("\tSSID: ").append(info.getSSID()).append("\n");
		s.append("\tBSSID: ").append(info.getBSSID()).append("\n");
		s.append("\tMAC address: ").append(info.getMacAddress()).append("\n");
		s.append("\tIP address: ")
				.append(ipToString(info.getIpAddress())).append("\n");
		s.append("\tSupplicant state: ")
				.append(info.getSupplicantState()).append("\n");
		s.append("\tNetwork ID: ").append(info.getNetworkId()).append("\n");
		s.append("\tLink speed: ").append(info.getLinkSpeed()).append("\n");
		s.append("\tRSSI: ").append(info.getRssi()).append("\n");
		if (info.getHiddenSSID()) s.append("\tHidden SSID\n");
		if (SDK_INT >= 21)
			s.append("\tFrequency: ").append(info.getFrequency()).append("\n");
	}

	private static void logNetworkInfo(StringBuilder s,
			@Nullable NetworkInfo info, boolean active) {
		if (info == null) {
			if (active) s.append("Active network info: null\n");
			else s.append("Network info: null\n");
			return;
		}
		if (active) s.append("Active network info:\n");
		else s.append("Network info:\n");
		s.append("\tType: ").append(info.getTypeName())
				.append(" (").append(info.getType()).append(")\n");
		s.append("\tSubtype: ").append(info.getSubtypeName())
				.append(" (").append(info.getSubtype()).append(")\n");
		s.append("\tState: ").append(info.getState()).append("\n");
		s.append("\tDetailed state: ")
				.append(info.getDetailedState()).append("\n");
		s.append("\tReason: ").append(info.getReason()).append("\n");
		s.append("\tExtra info: ").append(info.getExtraInfo()).append("\n");
		if (info.isAvailable()) s.append("\tAvailable\n");
		if (info.isConnected()) s.append("\tConnected\n");
		if (info.isConnectedOrConnecting())
			s.append("\tConnected or connecting\n");
		if (info.isFailover()) s.append("\tFailover\n");
		if (info.isRoaming()) s.append("\tRoaming\n");
	}

	private static void logNetworkInterface(StringBuilder s,
			NetworkInterface iface) throws SocketException {
		s.append("Network interface:\n");
		s.append("\tName: ").append(iface.getName()).append("\n");
		s.append("\tDisplay name: ")
				.append(iface.getDisplayName()).append("\n");
		s.append("\tHardware address: ")
				.append(hexOrNull(iface.getHardwareAddress())).append("\n");
		if (iface.isLoopback()) s.append("\tLoopback\n");
		if (iface.isPointToPoint()) s.append("\tPoint-to-point\n");
		if (iface.isVirtual()) s.append("\tVirtual\n");
		if (iface.isUp()) s.append("\tUp\n");
		if (SDK_INT >= 19)
			s.append("\tIndex: ").append(iface.getIndex()).append("\n");
		for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
			s.append("\tInterface address:\n");
			logInetAddress(s, addr.getAddress());
			s.append("\t\tPrefix length: ")
					.append(addr.getNetworkPrefixLength()).append("\n");
		}
	}

	private static void logInetAddress(StringBuilder s, InetAddress addr) {
		s.append("\t\tAddress: ")
				.append(hexOrNull(addr.getAddress())).append("\n");
		s.append("\t\tHost address: ")
				.append(addr.getHostAddress()).append("\n");
		if (addr.isLoopbackAddress()) s.append("\t\tLoopback\n");
		if (addr.isLinkLocalAddress()) s.append("\t\tLink-local\n");
		if (addr.isSiteLocalAddress()) s.append("\t\tSite-local\n");
		if (addr.isAnyLocalAddress()) s.append("\t\tAny local (wildcard)\n");
		if (addr.isMCNodeLocal()) s.append("\t\tMulticast node-local\n");
		if (addr.isMCLinkLocal()) s.append("\t\tMulticast link-local\n");
		if (addr.isMCSiteLocal()) s.append("\t\tMulticast site-local\n");
		if (addr.isMCOrgLocal()) s.append("\t\tMulticast org-local\n");
		if (addr.isMCGlobal()) s.append("\t\tMulticast global\n");
	}

	@Nullable
	private static String hexOrNull(@Nullable byte[] b) {
		return b == null ? null : toHexString(b);
	}
}
