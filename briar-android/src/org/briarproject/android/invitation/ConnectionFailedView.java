package org.briarproject.android.invitation;

import static android.view.Gravity.CENTER;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;
import org.briarproject.R;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class ConnectionFailedView extends AddContactView implements OnClickListener {

	private WifiStatusView wifi = null;
	private BluetoothStatusView bluetooth = null;
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
		icon.setImageResource(R.drawable.alerts_and_states_error);
		innerLayout.addView(icon);

		TextView failed = new TextView(ctx);
		failed.setTextSize(22);
		failed.setPadding(10, 10, 10, 10);
		failed.setText(R.string.connection_failed);
		innerLayout.addView(failed);
		addView(innerLayout);

		TextView checkNetwork = new TextView(ctx);
		checkNetwork.setTextSize(14);
		checkNetwork.setPadding(10, 0, 10, 10);
		checkNetwork.setText(R.string.check_same_network);
		addView(checkNetwork);

		wifi = new WifiStatusView(ctx);
		wifi.init();
		addView(wifi);

		bluetooth = new BluetoothStatusView(ctx);
		bluetooth.init();
		addView(bluetooth);

		tryAgainButton = new Button(ctx);
		tryAgainButton.setLayoutParams(WRAP_WRAP);
		tryAgainButton.setText(R.string.try_again_button);
		tryAgainButton.setOnClickListener(this);
		enableOrDisableTryAgainButton();
		addView(tryAgainButton);
	}

	void wifiStateChanged() {
		if(wifi != null) wifi.populate();
		enableOrDisableTryAgainButton();
	}

	void bluetoothStateChanged() {
		if(bluetooth != null) bluetooth.populate();
		enableOrDisableTryAgainButton();
	}

	private void enableOrDisableTryAgainButton() {
		if(tryAgainButton == null) return; // Activity not created yet
		boolean bluetoothEnabled = container.isBluetoothEnabled();
		String networkName = container.getNetworkName();
		tryAgainButton.setEnabled(bluetoothEnabled || networkName != null);
	}

	public void onClick(View view) {
		// Try again
		container.reset(new InvitationCodeView(container));
	}
}
