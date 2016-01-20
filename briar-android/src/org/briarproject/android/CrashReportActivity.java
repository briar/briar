package org.briarproject.android;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_EMAIL;
import static android.content.Intent.EXTRA_STREAM;
import static android.content.Intent.EXTRA_SUBJECT;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.TestingConstants.SHARE_CRASH_REPORTS;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

public class CrashReportActivity extends AppCompatActivity implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(CrashReportActivity.class.getName());

	private final AndroidExecutor androidExecutor =
			new AndroidExecutorImpl(getApplication());

	private ScrollView scroll = null;
	private ListLoadingProgressBar progress = null;
	private LinearLayout status = null;
	private File temp = null;

	private volatile String stack = null;
	private volatile int pid = -1;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		stack = i.getStringExtra("briar.STACK_TRACE");
		pid = i.getIntExtra("briar.PID", -1);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		scroll = new ScrollView(this);
		scroll.setLayoutParams(MATCH_WRAP_1);
		status = new LinearLayout(this);
		status.setOrientation(VERTICAL);
		status.setGravity(CENTER_HORIZONTAL);
		int pad = LayoutUtils.getPadding(this);
		status.setPadding(pad, pad, pad, pad);
		scroll.addView(status);
		layout.addView(scroll);

		progress = new ListLoadingProgressBar(this);
		progress.setVisibility(GONE);
		layout.addView(progress);

		if (SHARE_CRASH_REPORTS) {
			layout.addView(new HorizontalBorder(this));
			LinearLayout footer = new LinearLayout(this);
			footer.setLayoutParams(MATCH_WRAP);
			footer.setGravity(CENTER);
			Resources res = getResources();
			int background = res.getColor(R.color.button_bar_background);
			footer.setBackgroundColor(background);
			ImageButton share = new ImageButton(this);
			share.setBackgroundResource(0);
			share.setImageResource(R.drawable.social_share);
			share.setOnClickListener(this);
			footer.addView(share);
			layout.addView(footer);
		}

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		refresh();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (temp != null) temp.delete();
	}

	@Override
	public void onBackPressed() {
		// show home screen, otherwise we are crashing again
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		startActivity(intent);
	}

	public void onClick(View view) {
		share();
	}

	private void refresh() {
		status.removeAllViews();
		scroll.setVisibility(GONE);
		progress.setVisibility(VISIBLE);
		new AsyncTask<Void, Void, Map<String, String>>() {

			@Override
			protected Map<String, String> doInBackground(Void... args) {
				return getStatusMap();
			}

			@Override
			protected void onPostExecute(Map<String, String> result) {
				Context ctx = CrashReportActivity.this;
				int pad = LayoutUtils.getPadding(ctx);
				for (Entry<String, String> e : result.entrySet()) {
					TextView title = new TextView(ctx);
					title.setTextSize(18);
					title.setText(e.getKey());
					status.addView(title);
					TextView content = new TextView(ctx);
					content.setPadding(0, 0, 0, pad);
					content.setText(e.getValue());
					status.addView(content);
				}
				scroll.setVisibility(VISIBLE);
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
		BluetoothAdapter bt = null;
		try {
			bt = androidExecutor.submit(new Callable<BluetoothAdapter>() {
				public BluetoothAdapter call() throws Exception {
					return BluetoothAdapter.getDefaultAdapter();
				}
			}).get();
		} catch (InterruptedException e) {
			LOG.warning("Interrupted while getting BluetoothAdapter");
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
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

	private void share() {
		new AsyncTask<Void, Void, Map<String, String>>() {

			@Override
			protected Map<String, String> doInBackground(Void... args) {
				return getStatusMap();
			}

			@Override
			protected void onPostExecute(Map<String, String> result) {
				try {
					File shared = Environment.getExternalStorageDirectory();
					temp = File.createTempFile("crash", ".txt", shared);
					if (LOG.isLoggable(INFO))
						LOG.info("Writing to " + temp.getPath());
					PrintStream p = new PrintStream(new FileOutputStream(temp));
					for (Entry<String, String> e : result.entrySet()) {
						p.println(e.getKey());
						p.println(e.getValue());
						p.println();
					}
					p.flush();
					p.close();
					sendEmail(Uri.fromFile(temp));
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		}.execute();
	}

	private void sendEmail(Uri attachment) {
		Intent i = new Intent(ACTION_SEND);
		i.setType("message/rfc822");
		i.putExtra(EXTRA_EMAIL, new String[] { "briartest@gmail.com" });
		i.putExtra(EXTRA_SUBJECT, "Crash report");
		i.putExtra(EXTRA_STREAM, attachment);
		startActivity(Intent.createChooser(i, "Send to developers"));
	}
}
