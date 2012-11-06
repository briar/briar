package net.sf.briar.android.invitation;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
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
		setContentView(R.layout.activity_network_setup);
		LinearLayout layout = (LinearLayout) findViewById(
				R.id.network_setup_container);

		TextView sameNetwork = new TextView(this);
		sameNetwork.setText(R.string.same_network);
		layout.addView(sameNetwork);
		wifi = new WifiWidget(this);
		wifi.init(this);
		layout.addView(wifi);
		bluetooth = new BluetoothWidget(this);
		bluetooth.init(this);
		layout.addView(bluetooth);
		continueButton = new Button(this);
		continueButton.setText(R.string.continue_button);
		continueButton.setOnClickListener(this);
		setContinueButtonVisibility();
		layout.addView(continueButton);
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
				setContinueButtonVisibility();
			}
		});
	}

	public void bluetoothStateChanged(final boolean enabled) {
		runOnUiThread(new Runnable() {
			public void run() {
				useBluetooth = enabled;
				setContinueButtonVisibility();
			}
		});
	}

	private void setContinueButtonVisibility() {
		if(continueButton == null) return;
		if(useBluetooth || networkName != null)
			continueButton.setVisibility(VISIBLE);
		else continueButton.setVisibility(INVISIBLE);
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
