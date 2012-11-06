package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.VERTICAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NetworkSetupActivity extends Activity
implements WifiStateListener, BluetoothStateListener, OnClickListener {

	private WifiWidget wifi = null;
	private BluetoothWidget bluetooth = null;
	private Button continueButton = null;
	private String networkName = null;
	private boolean useBluetooth = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		TextView sameNetwork = new TextView(this);
		sameNetwork.setGravity(CENTER_HORIZONTAL);
		sameNetwork.setText(R.string.same_network);
		layout.addView(sameNetwork);

		wifi = new WifiWidget(this);
		wifi.init(this);
		layout.addView(wifi);

		bluetooth = new BluetoothWidget(this);
		bluetooth.init(this);
		layout.addView(bluetooth);

		continueButton = new Button(this);
		LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		continueButton.setLayoutParams(lp);
		continueButton.setText(R.string.continue_button);
		continueButton.setOnClickListener(this);
		enableOrDisableContinueButton();
		layout.addView(continueButton);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		wifi.populate();
		bluetooth.populate();
	}

	public void wifiStateChanged(final String name) {
		runOnUiThread(new Runnable() {
			public void run() {
				networkName = name;
				enableOrDisableContinueButton();
			}
		});
	}

	public void bluetoothStateChanged(final boolean enabled) {
		runOnUiThread(new Runnable() {
			public void run() {
				useBluetooth = enabled;
				enableOrDisableContinueButton();
			}
		});
	}

	private void enableOrDisableContinueButton() {
		if(continueButton == null) return; // Activity not created yet
		if(useBluetooth || networkName != null) continueButton.setEnabled(true);
		else continueButton.setEnabled(false);
	}

	public void onClick(View view) {
		Intent intent = new Intent(this, InvitationCodeActivity.class);
		intent.putExtra("net.sf.briar.android.invitation.NETWORK_NAME",
				networkName);
		intent.putExtra("net.sf.briar.android.invitation.USE_BLUETOOTH",
				useBluetooth);
		startActivity(intent);
		finish();
	}
}
