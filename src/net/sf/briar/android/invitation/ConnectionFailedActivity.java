package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.HORIZONTAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
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
		setContentView(R.layout.activity_connection_failed);
		LinearLayout outerLayout = (LinearLayout) findViewById(
				R.id.connection_failed_container);

		LinearLayout innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);
		ImageView icon = new ImageView(this);
		icon.setImageResource(R.drawable.iconic_x_alt_red);
		icon.setPadding(10, 10, 10, 10);
		innerLayout.addView(icon);
		TextView failed = new TextView(this);
		failed.setTextSize(20);
		failed.setText(R.string.connection_failed);
		innerLayout.addView(failed);
		outerLayout.addView(innerLayout);

		TextView checkNetwork = new TextView(this);
		checkNetwork.setText(R.string.check_same_network);
		outerLayout.addView(checkNetwork);
		wifi = new WifiWidget(this);
		wifi.init(this);
		outerLayout.addView(wifi);
		bluetooth = new BluetoothWidget(this);
		bluetooth.init(this);
		outerLayout.addView(bluetooth);
		tryAgainButton = new Button(this);
		tryAgainButton.setText(R.string.try_again_button);
		tryAgainButton.setOnClickListener(this);
		setTryAgainButtonVisibility();
		outerLayout.addView(tryAgainButton);
	}

	@Override
	public void onResume() {
		super.onResume();
		wifi.populate();
		bluetooth.populate();
	}

	public void wifiStateChanged(String networkName) {
		this.networkName = networkName;
		setTryAgainButtonVisibility();
	}

	public void bluetoothStateChanged(boolean enabled) {
		useBluetooth = enabled;
		setTryAgainButtonVisibility();
	}

	private void setTryAgainButtonVisibility() {
		if(tryAgainButton == null) return;
		if(useBluetooth || networkName != null)
			tryAgainButton.setVisibility(VISIBLE);
		else tryAgainButton.setVisibility(INVISIBLE);
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
