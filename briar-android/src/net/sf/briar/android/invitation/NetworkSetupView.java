package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
import net.sf.briar.android.widgets.CommonLayoutParams;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class NetworkSetupView extends AddContactView
implements WifiStateListener, BluetoothStateListener, OnClickListener {

	private Button continueButton = null;

	NetworkSetupView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		TextView sameNetwork = new TextView(ctx);
		sameNetwork.setGravity(CENTER_HORIZONTAL);
		sameNetwork.setText(R.string.same_network);
		addView(sameNetwork);

		WifiWidget wifi = new WifiWidget(ctx);
		wifi.init(this);
		addView(wifi);

		BluetoothWidget bluetooth = new BluetoothWidget(ctx);
		bluetooth.init(this);
		addView(bluetooth);

		continueButton = new Button(ctx);
		continueButton.setLayoutParams(CommonLayoutParams.WRAP_WRAP);
		continueButton.setText(R.string.continue_button);
		continueButton.setOnClickListener(this);
		enableOrDisableContinueButton();
		addView(continueButton);
	}

	public void wifiStateChanged(final String networkName) {
		container.runOnUiThread(new Runnable() {
			public void run() {
				container.setNetworkName(networkName);
				enableOrDisableContinueButton();
			}
		});
	}

	public void bluetoothStateChanged(final boolean enabled) {
		container.runOnUiThread(new Runnable() {
			public void run() {
				container.setUseBluetooth(enabled);
				enableOrDisableContinueButton();
			}
		});
	}

	private void enableOrDisableContinueButton() {
		if(continueButton == null) return; // Activity not created yet
		boolean useBluetooth = container.getUseBluetooth();
		String networkName = container.getNetworkName();
		if(useBluetooth || networkName != null) continueButton.setEnabled(true);
		else continueButton.setEnabled(false);
	}

	public void onClick(View view) {
		// Continue
		container.setView(new InvitationCodeView(container));
	}
}
