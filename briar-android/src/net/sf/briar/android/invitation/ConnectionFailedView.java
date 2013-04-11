package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP;
import net.sf.briar.R;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ConnectionFailedView extends AddContactView
implements WifiStateListener, BluetoothStateListener, OnClickListener {

	private Button tryAgainButton = null;

	ConnectionFailedView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(ctx);
		icon.setPadding(10, 10, 10, 10);
		icon.setImageResource(R.drawable.alerts_and_states_error);
		innerLayout.addView(icon);

		TextView failed = new TextView(ctx);
		failed.setTextSize(22);
		failed.setPadding(0, 10, 10, 10);
		failed.setText(R.string.connection_failed);
		innerLayout.addView(failed);
		addView(innerLayout);

		TextView checkNetwork = new TextView(ctx);
		checkNetwork.setTextSize(14);
		checkNetwork.setPadding(10, 0, 10, 10);
		checkNetwork.setText(R.string.check_same_network);
		addView(checkNetwork);

		WifiWidget wifi = new WifiWidget(ctx);
		wifi.init(this);
		addView(wifi);

		BluetoothWidget bluetooth = new BluetoothWidget(ctx);
		bluetooth.init(this);
		addView(bluetooth);

		tryAgainButton = new Button(ctx);
		tryAgainButton.setLayoutParams(WRAP_WRAP);
		tryAgainButton.setText(R.string.try_again_button);
		tryAgainButton.setOnClickListener(this);
		enableOrDisableTryAgainButton();
		addView(tryAgainButton);
	}

	public void wifiStateChanged(final String networkName) {
		container.runOnUiThread(new Runnable() {
			public void run() {
				container.setNetworkName(networkName);
				enableOrDisableTryAgainButton();
			}
		});
	}

	public void bluetoothStateChanged(final boolean enabled) {
		container.runOnUiThread(new Runnable() {
			public void run() {
				container.setUseBluetooth(enabled);
				enableOrDisableTryAgainButton();
			}
		});
	}

	private void enableOrDisableTryAgainButton() {
		if(tryAgainButton == null) return; // Activity not created yet
		boolean useBluetooth = container.getUseBluetooth();
		String networkName = container.getNetworkName();
		tryAgainButton.setEnabled(useBluetooth || networkName != null);
	}

	public void onClick(View view) {
		// Try again
		container.reset(new InvitationCodeView(container));
	}
}
