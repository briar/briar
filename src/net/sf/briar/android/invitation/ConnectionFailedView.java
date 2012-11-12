package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
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
		failed.setTextSize(20);
		failed.setText(R.string.connection_failed);
		innerLayout.addView(failed);
		addView(innerLayout);

		TextView checkNetwork = new TextView(ctx);
		checkNetwork.setGravity(CENTER_HORIZONTAL);
		checkNetwork.setText(R.string.check_same_network);
		addView(checkNetwork);

		WifiWidget wifi = new WifiWidget(ctx);
		wifi.init(this);
		addView(wifi);

		BluetoothWidget bluetooth = new BluetoothWidget(ctx);
		bluetooth.init(this);
		addView(bluetooth);

		tryAgainButton = new Button(ctx);
		LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		tryAgainButton.setLayoutParams(lp);
		tryAgainButton.setText(R.string.try_again_button);
		tryAgainButton.setOnClickListener(this);
		enabledOrDisableTryAgainButton();
		addView(tryAgainButton);
	}

	public void wifiStateChanged(String networkName) {
		container.setNetworkName(networkName);
		enabledOrDisableTryAgainButton();
	}

	public void bluetoothStateChanged(boolean enabled) {
		container.setUseBluetooth(enabled);
		enabledOrDisableTryAgainButton();
	}

	private void enabledOrDisableTryAgainButton() {
		if(tryAgainButton == null) return; // Activity not created yet
		boolean useBluetooth = container.getUseBluetooth();
		String networkName = container.getNetworkName();
		if(useBluetooth || networkName != null) tryAgainButton.setEnabled(true);
		else tryAgainButton.setEnabled(false);
	}

	public void onClick(View view) {
		// Try again
		container.setView(new InvitationCodeView(container));
	}
}
