package org.briarproject.android;

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
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.util.ElasticHorizontalSpace;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.util.StringUtils;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TestingActivity extends BriarActivity implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(TestingActivity.class.getName());

	@Inject private AndroidExecutor androidExecutor;
	@Inject private PluginManager pluginManager;
	private ScrollView scroll = null;
	private LinearLayout status = null;
	private ImageButton refresh = null, share = null;
	private File temp = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

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

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setGravity(CENTER);
		Resources res = getResources();
		footer.setBackgroundColor(res.getColor(R.color.button_bar_background));
		footer.addView(new ElasticHorizontalSpace(this));

		refresh = new ImageButton(this);
		refresh.setBackgroundResource(0);
		refresh.setImageResource(R.drawable.navigation_refresh);
		refresh.setOnClickListener(this);
		footer.addView(refresh);
		footer.addView(new ElasticHorizontalSpace(this));

		share = new ImageButton(this);
		share.setBackgroundResource(0);
		share.setImageResource(R.drawable.social_share);
		share.setOnClickListener(this);
		footer.addView(share);
		footer.addView(new ElasticHorizontalSpace(this));
		layout.addView(footer);

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
		if(temp != null) temp.delete();
	}

	public void onClick(View view) {
		if(view == refresh) refresh();
		else if(view == share) share();
	}

	private void refresh() {
		status.removeAllViews();
		new AsyncTask<Void, Void, Map<String, String>>() {

			protected Map<String, String> doInBackground(Void... args) {
				return getStatusMap();
			}

			protected void onPostExecute(Map<String, String> result) {
				int pad = LayoutUtils.getPadding(TestingActivity.this);
				for(Entry<String, String> e : result.entrySet()) {
					TextView title = new TextView(TestingActivity.this);
					title.setTextSize(18);
					title.setText(e.getKey());
					status.addView(title);
					TextView content = new TextView(TestingActivity.this);
					content.setPadding(0, 0, 0, pad);
					content.setText(e.getValue());
					status.addView(content);
				}
				scroll.scrollTo(0, 0);
			}
		}.execute();
	}

	private Map<String, String> getStatusMap() {
		Map<String, String> statusMap = new LinkedHashMap<String, String>();
		// Is mobile data available?
		Object o = getSystemService(CONNECTIVITY_SERVICE);
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
		} catch(ClassNotFoundException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch(NoSuchMethodException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch(IllegalAccessException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch(IllegalArgumentException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch(InvocationTargetException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
		// Is mobile data connected ?
		boolean mobileConnected = mobile != null && mobile.isConnected();

		// Strings aren't loaded from resources as this activity is temporary
		String mobileStatus;
		if(mobileAvailable) mobileStatus = "Available, ";
		else mobileStatus = "Not available, ";
		if(mobileEnabled) mobileStatus += "enabled, ";
		else mobileStatus += "not enabled, ";
		if(mobileConnected) mobileStatus += "connected";
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
		if(wifiAvailable) wifiStatus = "Available, ";
		else wifiStatus = "Not available, ";
		if(wifiEnabled) wifiStatus += "enabled, ";
		else wifiStatus += "not enabled, ";
		if(wifiConnected) wifiStatus += "connected";
		else wifiStatus += "not connected";
		statusMap.put("Wi-Fi:", wifiStatus);

		// Is Bluetooth available?
		BluetoothAdapter bt = null;
		try {
			bt = androidExecutor.call(new Callable<BluetoothAdapter>() {
				public BluetoothAdapter call() throws Exception {
					return BluetoothAdapter.getDefaultAdapter();
				}
			});
		} catch(InterruptedException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch(ExecutionException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
		if(btAvailable) btStatus = "Available, ";
		else btStatus = "Not available, ";
		if(btEnabled) btStatus += "enabled, ";
		else btStatus += "not enabled, ";
		if(btConnectable) btStatus += "connectable, ";
		else btStatus += "not connectable, ";
		if(btDiscoverable) btStatus += "discoverable";
		else btStatus += "not discoverable";
		statusMap.put("Bluetooth:", btStatus);

		Plugin torPlugin = pluginManager.getPlugin(new TransportId("tor"));
		boolean torPluginEnabled = torPlugin != null;
		boolean torPluginRunning = torPlugin != null && torPlugin.isRunning();

		String torPluginStatus;
		if(torPluginEnabled) torPluginStatus = "Enabled, ";
		else torPluginStatus = "Not enabled, ";
		if(torPluginRunning) torPluginStatus += "running";
		else torPluginStatus += "not running";
		statusMap.put("Tor plugin:", torPluginStatus);

		Plugin lanPlugin = pluginManager.getPlugin(new TransportId("lan"));
		boolean lanPluginEnabled = lanPlugin != null;
		boolean lanPluginRunning = lanPlugin != null && lanPlugin.isRunning();

		String lanPluginStatus;
		if(lanPluginEnabled) lanPluginStatus = "Enabled, ";
		else lanPluginStatus = "Not enabled, ";
		if(lanPluginRunning) lanPluginStatus += "running";
		else lanPluginStatus += "not running";
		statusMap.put("LAN plugin:", lanPluginStatus);

		Plugin btPlugin = pluginManager.getPlugin(new TransportId("bt"));
		boolean btPluginEnabled = btPlugin != null;
		boolean btPluginRunning = btPlugin != null && btPlugin.isRunning();

		String btPluginStatus;
		if(btPluginEnabled) btPluginStatus = "Enabled, ";
		else btPluginStatus = "Not enabled, ";
		if(btPluginRunning) btPluginStatus += "running";
		else btPluginStatus += "not running";
		statusMap.put("Bluetooth plugin:", btPluginStatus);

		StringBuilder log = new StringBuilder();
		try {
			Runtime runtime = Runtime.getRuntime();
			Process process = runtime.exec("logcat -d -s TorPlugin");
			Scanner scanner = new Scanner(process.getInputStream());
			while(scanner.hasNextLine()) {
				log.append(scanner.nextLine());
				log.append('\n');
			}
			scanner.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
		statusMap.put("Tor log:", log.toString());

		return Collections.unmodifiableMap(statusMap);
	}

	private void share() {
		new AsyncTask<Void, Void, Map<String, String>>() {

			protected Map<String, String> doInBackground(Void... args) {
				return getStatusMap();
			}

			protected void onPostExecute(Map<String, String> result) {
				try {
					File shared = Environment.getExternalStorageDirectory();
					temp = File.createTempFile("debug", "txt", shared);
					if(LOG.isLoggable(INFO))
						LOG.info("Writing to " + temp.getPath());
					PrintStream p = new PrintStream(new FileOutputStream(temp));
					for(Entry<String, String> e : result.entrySet()) {
						p.println(e.getKey());
						p.println(e.getValue());
						p.println();
					}
					p.flush();
					p.close();
					sendEmail(Uri.fromFile(temp));
				} catch(IOException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		}.execute();
	}

	private void sendEmail(Uri attachment) {
		Intent i = new Intent(ACTION_SEND);
		i.setType("message/rfc822");
		i.putExtra(EXTRA_EMAIL, new String[] { "debug@briarproject.org" });
		i.putExtra(EXTRA_SUBJECT, "Debugging information");
		i.putExtra(EXTRA_STREAM, attachment);
		startActivity(Intent.createChooser(i, "Send to developers"));
	}
}
