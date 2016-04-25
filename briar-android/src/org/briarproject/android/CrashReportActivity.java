package org.briarproject.android;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.api.reporting.DevReporter;
import org.briarproject.util.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static java.util.logging.Level.WARNING;

public class CrashReportActivity extends AppCompatActivity
		implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(CrashReportActivity.class.getName());

	private LinearLayout status = null;
	private View progress = null;

	@Inject
	protected DevReporter reporter;

	private volatile String stack = null;
	private volatile int pid = -1;
	private volatile BluetoothAdapter bt = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_crash);

		((BriarApplication) getApplication()).getApplicationComponent()
				.inject(this);

		status = (LinearLayout) findViewById(R.id.crash_status);
		progress = findViewById(R.id.progress_wheel);

		findViewById(R.id.share_crash_report).setOnClickListener(this);

		Intent i = getIntent();
		stack = i.getStringExtra("briar.STACK_TRACE");
		pid = i.getIntExtra("briar.PID", -1);
		bt = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public void onResume() {
		super.onResume();
		refresh();
	}

	@Override
	public void onBackPressed() {
		// show home screen, otherwise we are crashing again
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		startActivity(intent);
	}

	public void onClick(View view) {
		// TODO Encapsulate the dialog in a re-usable fragment
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.dialog_title_share_crash_report);
		builder.setMessage(R.string.dialog_message_share_crash_report);
		builder.setNegativeButton(R.string.cancel_button, null);
		builder.setPositiveButton(R.string.send,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						saveCrashReport();
					}
				});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void refresh() {
		status.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		status.removeAllViews();
		new AsyncTask<Void, Void, Map<String, String>>() {

			@Override
			protected Map<String, String> doInBackground(Void... args) {
				return getStatusMap();
			}

			@Override
			protected void onPostExecute(Map<String, String> result) {
				for (Entry<String, String> e : result.entrySet()) {
					View v = getLayoutInflater()
							.inflate(R.layout.list_item_crash, status, false);
					((TextView) v.findViewById(R.id.title)).setText(e.getKey());
					((TextView) v.findViewById(R.id.content))
							.setText(e.getValue());
					status.addView(v);
				}
				status.setVisibility(VISIBLE);
				progress.setVisibility(GONE);
			}
		}.execute();
	}

	// FIXME: Load strings from resources if we're keeping this activity
	@SuppressLint("NewApi")
	private Map<String, String> getStatusMap() {
		Map<String, String> statusMap = new LinkedHashMap<String, String>();

		// Device type
		String deviceType;
		String manufacturer = Build.MANUFACTURER;
		String model = Build.MODEL;
		String brand = Build.BRAND;
		if (model.startsWith(manufacturer)) deviceType = capitalize(model);
		else deviceType = capitalize(manufacturer) + " " + model;
		if (!StringUtils.isNullOrEmpty(brand))
			deviceType += " (" + capitalize(brand) + ")";
		statusMap.put("Device type:", deviceType);

		// Android version
		String release = Build.VERSION.RELEASE;
		int sdk = Build.VERSION.SDK_INT;
		statusMap.put("Android version:", release + " (" + sdk + ")");

		// CPU architectures
		Collection<String> abis = AndroidUtils.getSupportedArchitectures();
		String joined = StringUtils.join(abis, ", ");
		statusMap.put("Architecture:", joined);

		// System memory
		Object o = getSystemService(ACTIVITY_SERVICE);
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
		statusMap.put("System memory:", systemMemory);

		// Virtual machine memory
		Runtime runtime = Runtime.getRuntime();
		long heap = runtime.totalMemory();
		long heapFree = runtime.freeMemory();
		long heapMax = runtime.maxMemory();
		String vmMemory = (heap / 1024 / 1024) + " MiB allocated, "
				+ (heapFree / 1024 / 1024) + " MiB free, "
				+ (heapMax / 1024 / 1024) + " MiB maximum";
		statusMap.put("Virtual machine memory:", vmMemory);

		// Internal storage
		File root = Environment.getRootDirectory();
		long rootTotal = root.getTotalSpace();
		long rootFree = root.getFreeSpace();
		String internal = (rootTotal / 1024 / 1024) + " MiB total, "
				+ (rootFree / 1024 / 1024) + " MiB free";
		statusMap.put("Internal storage:", internal);

		// External storage (SD card)
		File sd = Environment.getExternalStorageDirectory();
		long sdTotal = sd.getTotalSpace();
		long sdFree = sd.getFreeSpace();
		String external = (sdTotal / 1024 / 1024) + " MiB total, "
				+ (sdFree / 1024 / 1024) + " MiB free";
		statusMap.put("External storage:", external);

		// Is mobile data available?
		o = getSystemService(CONNECTIVITY_SERVICE);
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
		statusMap.put("Mobile data:", mobileStatus);

		// Is wifi available?
		NetworkInfo wifi = cm.getNetworkInfo(TYPE_WIFI);
		boolean wifiAvailable = wifi != null && wifi.isAvailable();
		// Is wifi enabled?
		WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
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
		if (wm != null) {
			WifiInfo wifiInfo = wm.getConnectionInfo();
			if (wifiInfo != null) {
				int ip = wifiInfo.getIpAddress(); // Nice API, Google
				int ip1 = ip & 0xFF;
				int ip2 = (ip >> 8) & 0xFF;
				int ip3 = (ip >> 16) & 0xFF;
				int ip4 = (ip >> 24) & 0xFF;
				String address = ip1 + "." + ip2 + "." + ip3 + "." + ip4;
				wifiStatus += "\nAddress: " + address;
			}
		}
		statusMap.put("Wi-Fi:", wifiStatus);

		// Is Bluetooth available?
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
		if (bt != null) btStatus += "\nAddress: " + bt.getAddress();
		try {
			String btAddr = Settings.Secure.getString(getContentResolver(),
					"bluetooth_address");
			btStatus += "\nAddress from settings: " + btAddr;
		} catch (SecurityException e) {
			btStatus += "\nCould not get address from settings";
		}
		statusMap.put("Bluetooth:", btStatus);

		// Stack trace
		if (stack != null) statusMap.put("Stack trace:", stack);

		// All log output from the crashed process
		if (pid != -1) {
			StringBuilder log = new StringBuilder();
			try {
				Pattern pattern = Pattern.compile(".*\\( *" + pid + "\\).*");
				Process process = runtime.exec("logcat -d -v time *:I");
				Scanner scanner = new Scanner(process.getInputStream());
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					if (pattern.matcher(line).matches()) {
						log.append(line);
						log.append('\n');
					}
				}
				scanner.close();
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
			statusMap.put("Debugging log:", log.toString());
		}

		return Collections.unmodifiableMap(statusMap);
	}

	private String capitalize(String s) {
		if (StringUtils.isNullOrEmpty(s)) return s;
		char first = s.charAt(0);
		if (Character.isUpperCase(first)) return s;
		return Character.toUpperCase(first) + s.substring(1);
	}

	private void saveCrashReport() {
		StringBuilder s = new StringBuilder();
		for (Entry<String, String> e : getStatusMap().entrySet()) {
			s.append(e.getKey());
			s.append('\n');
			s.append(e.getValue());
			s.append("\n\n");
		}
		final String crashReport = s.toString();
		try {
			reporter.encryptCrashReportToFile(
					AndroidUtils.getCrashReportDir(this), crashReport);
			Toast.makeText(this, R.string.crash_report_saved, Toast.LENGTH_LONG)
					.show();
			finish();
		} catch (FileNotFoundException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, "Error while saving encrypted crash report",
						e);
			Toast.makeText(this, R.string.crash_report_not_saved,
					Toast.LENGTH_SHORT).show();
		}
	}
}
