package net.sf.briar.android.invitation;

import static android.content.Context.WIFI_SERVICE;
import static android.provider.Settings.ACTION_WIFI_SETTINGS;
import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WifiWidget extends LinearLayout implements OnClickListener {

	private WifiStateListener listener = null;

	public WifiWidget(Context ctx) {
		super(ctx);
	}

	void init(WifiStateListener listener) {
		this.listener = listener;
		setOrientation(VERTICAL);
		setPadding(0, 10, 0, 0);
		populate();
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		TextView status = new TextView(ctx);
		status.setGravity(CENTER_HORIZONTAL);
		WifiManager wifi = (WifiManager) ctx.getSystemService(WIFI_SERVICE);
		if(wifi == null) {
			wifiStateChanged(null);
			status.setText(R.string.wifi_not_available);
			addView(status);
		} else if(wifi.isWifiEnabled()) { 
			String networkName =  wifi.getConnectionInfo().getSSID();
			if(networkName == null) {
				wifiStateChanged(null);
				status.setText(R.string.wifi_disconnected);
				addView(status);
				Button connect = new Button(ctx);
				connect.setText(R.string.connect_to_wifi_button);
				connect.setOnClickListener(this);
				addView(connect);
			} else {
				wifiStateChanged(networkName);
				Resources res = getResources();
				String connected = res.getString(R.string.wifi_connected);
				status.setText(String.format(connected, networkName));
				addView(status);
			}
		} else {
			wifiStateChanged(null);
			status.setText(R.string.wifi_disabled);
			addView(status);
			Button connect = new Button(ctx);
			connect.setText(R.string.connect_to_wifi_button);
			connect.setOnClickListener(this);
			addView(connect);
		}
	}

	private void wifiStateChanged(String networkName) {
		if(listener != null) listener.wifiStateChanged(networkName);
	}

	public void onClick(View view) {
		getContext().startActivity(new Intent(ACTION_WIFI_SETTINGS));
	}
}
