package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ConnectionFailedActivity extends Activity
implements WifiStateListener, BluetoothStateListener, OnClickListener {

	private WifiWidget wifi = null;
	private BluetoothWidget bluetooth = null;
	private Button tryAgainButton = null;
	private String networkName = null;
	private boolean useBluetooth = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		LinearLayout innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(this);
		icon.setPadding(10, 10, 10, 10);
		icon.setImageResource(R.drawable.alerts_and_states_error);
		innerLayout.addView(icon);

		TextView failed = new TextView(this);
		failed.setTextSize(20);
		failed.setText(R.string.connection_failed);
		innerLayout.addView(failed);
		layout.addView(innerLayout);

		TextView checkNetwork = new TextView(this);
		checkNetwork.setText(R.string.check_same_network);
		layout.addView(checkNetwork);

		wifi = new WifiWidget(this);
		wifi.init(this);
		layout.addView(wifi);

		bluetooth = new BluetoothWidget(this);
		bluetooth.init(this);
		layout.addView(bluetooth);

		tryAgainButton = new Button(this);
		LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		tryAgainButton.setLayoutParams(lp);
		tryAgainButton.setText(R.string.try_again_button);
		tryAgainButton.setOnClickListener(this);
		enabledOrDisableTryAgainButton();
		layout.addView(tryAgainButton);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		wifi.populate();
		bluetooth.populate();
	}

	public void wifiStateChanged(String networkName) {
		this.networkName = networkName;
		enabledOrDisableTryAgainButton();
	}

	public void bluetoothStateChanged(boolean enabled) {
		useBluetooth = enabled;
		enabledOrDisableTryAgainButton();
	}

	private void enabledOrDisableTryAgainButton() {
		if(tryAgainButton == null) return; // Activity not created yet
		if(useBluetooth || networkName != null) tryAgainButton.setEnabled(true);
		else tryAgainButton.setEnabled(false);
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
