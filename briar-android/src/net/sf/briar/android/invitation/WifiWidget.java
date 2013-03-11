package net.sf.briar.android.invitation;

import static android.content.Context.WIFI_SERVICE;
import static android.provider.Settings.ACTION_WIFI_SETTINGS;
import static android.view.Gravity.CENTER;
import net.sf.briar.R;
import net.sf.briar.android.widgets.CommonLayoutParams;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WifiWidget extends LinearLayout implements OnClickListener {

	private WifiStateListener listener = null;

	public WifiWidget(Context ctx) {
		super(ctx);
	}

	void init(WifiStateListener listener) {
		this.listener = listener;
		setOrientation(HORIZONTAL);
		setGravity(CENTER);
		populate();
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		TextView status = new TextView(ctx);
		status.setTextSize(14);
		status.setPadding(10, 10, 10, 10);
		status.setLayoutParams(CommonLayoutParams.WRAP_WRAP_1);
		WifiManager wifi = (WifiManager) ctx.getSystemService(WIFI_SERVICE);
		if(wifi == null) {
			wifiStateChanged(null);
			ImageView warning = new ImageView(ctx);
			warning.setImageResource(R.drawable.alerts_and_states_warning);
			warning.setPadding(10, 10, 10, 10);
			addView(warning);
			status.setText(R.string.wifi_not_available);
			addView(status);
		} else if(wifi.isWifiEnabled()) { 
			WifiInfo info = wifi.getConnectionInfo();
			String networkName =  info.getSSID();
			int networkId = info.getNetworkId();
			if(networkName == null || networkId == -1) {
				wifiStateChanged(null);
				ImageView warning = new ImageView(ctx);
				warning.setImageResource(R.drawable.alerts_and_states_warning);
				warning.setPadding(10, 10, 10, 10);
				addView(warning);
				status.setText(R.string.wifi_disconnected);
				addView(status);
				ImageButton settings = new ImageButton(ctx);
				settings.setImageResource(R.drawable.action_settings);
				settings.setOnClickListener(this);
				addView(settings);
			} else {
				wifiStateChanged(networkName);
				ImageView ok = new ImageView(ctx);
				ok.setImageResource(R.drawable.navigation_accept);
				ok.setPadding(10, 10, 10, 10);
				addView(ok);
				Resources res = getResources();
				String connected = res.getString(R.string.wifi_connected);
				status.setText(String.format(connected, networkName));
				addView(status);
				ImageButton settings = new ImageButton(ctx);
				settings.setImageResource(R.drawable.action_settings);
				settings.setOnClickListener(this);
				addView(settings);
			}
		} else {
			wifiStateChanged(null);
			ImageView warning = new ImageView(ctx);
			warning.setImageResource(R.drawable.alerts_and_states_warning);
			warning.setPadding(10, 10, 10, 10);
			addView(warning);
			status.setText(R.string.wifi_disabled);
			addView(status);
			ImageButton settings = new ImageButton(ctx);
			settings.setImageResource(R.drawable.action_settings);
			settings.setOnClickListener(this);
			addView(settings);
		}
	}

	private void wifiStateChanged(String networkName) {
		if(listener != null) listener.wifiStateChanged(networkName);
	}

	public void onClick(View view) {
		getContext().startActivity(new Intent(ACTION_WIFI_SETTINGS));
	}
}
