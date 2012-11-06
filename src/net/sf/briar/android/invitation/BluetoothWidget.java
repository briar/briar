package net.sf.briar.android.invitation;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.provider.Settings.ACTION_BLUETOOTH_SETTINGS;
import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BluetoothWidget extends LinearLayout implements OnClickListener {

	private BluetoothStateListener listener = null;

	public BluetoothWidget(Context ctx) {
		super(ctx);
	}

	void init(BluetoothStateListener listener) {
		this.listener = listener;
		setOrientation(VERTICAL);
		setPadding(0, 10, 0, 10);
		populate();
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		TextView status = new TextView(ctx);
		status.setGravity(CENTER_HORIZONTAL);
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if(adapter == null) {
			bluetoothStateChanged(false);
			status.setText(R.string.bluetooth_not_available);
			addView(status);
		} else if(adapter.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			bluetoothStateChanged(true);
			status.setText(R.string.bluetooth_enabled);
			addView(status);
		} else if(adapter.isEnabled()) {
			bluetoothStateChanged(false);
			status.setText(R.string.bluetooth_not_discoverable);
			addView(status);
			Button turnOn = new Button(ctx);
			turnOn.setText(R.string.make_bluetooth_discoverable_button);
			turnOn.setOnClickListener(this);
			addView(turnOn);
		} else {
			bluetoothStateChanged(false);
			status.setText(R.string.bluetooth_disabled);
			addView(status);
			Button turnOn = new Button(ctx);
			turnOn.setText(R.string.turn_on_bluetooth_button);
			turnOn.setOnClickListener(this);
			addView(turnOn);
		}
	}

	private void bluetoothStateChanged(boolean enabled) {
		listener.bluetoothStateChanged(enabled);
	}

	public void onClick(View view) {
		getContext().startActivity(new Intent(ACTION_BLUETOOTH_SETTINGS));
	}
}
