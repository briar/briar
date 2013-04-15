package net.sf.briar.android.invitation;

import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.provider.Settings.ACTION_BLUETOOTH_SETTINGS;
import static android.view.Gravity.CENTER;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP_1;
import net.sf.briar.R;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BluetoothWidget extends LinearLayout implements OnClickListener {

	private BluetoothStateListener listener = null;

	public BluetoothWidget(Context ctx) {
		super(ctx);
	}

	void init(BluetoothStateListener listener) {
		this.listener = listener;
		setOrientation(HORIZONTAL);
		setGravity(CENTER);
		populate();
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		TextView status = new TextView(ctx);
		status.setLayoutParams(WRAP_WRAP_1);
		status.setTextSize(14);
		status.setPadding(10, 10, 10, 10);
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if(adapter == null) {
			listener.bluetoothStateChanged(false);
			ImageView warning = new ImageView(ctx);
			warning.setImageResource(R.drawable.alerts_and_states_warning);
			addView(warning);
			status.setText(R.string.bluetooth_not_available);
			addView(status);
		} else if(adapter.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			listener.bluetoothStateChanged(true);
			ImageView ok = new ImageView(ctx);
			ok.setImageResource(R.drawable.navigation_accept);
			addView(ok);
			status.setText(R.string.bluetooth_enabled);
			addView(status);
			ImageButton settings = new ImageButton(ctx);
			settings.setImageResource(R.drawable.action_settings);
			settings.setOnClickListener(this);
			addView(settings);
		} else if(adapter.isEnabled()) {
			listener.bluetoothStateChanged(true);
			ImageView warning = new ImageView(ctx);
			warning.setImageResource(R.drawable.alerts_and_states_warning);
			addView(warning);
			status.setText(R.string.bluetooth_not_discoverable);
			addView(status);
			ImageButton settings = new ImageButton(ctx);
			settings.setImageResource(R.drawable.action_settings);
			settings.setOnClickListener(this);
			addView(settings);
		} else {
			listener.bluetoothStateChanged(false);
			ImageView warning = new ImageView(ctx);
			warning.setImageResource(R.drawable.alerts_and_states_warning);
			addView(warning);
			status.setText(R.string.bluetooth_disabled);
			addView(status);
			ImageButton settings = new ImageButton(ctx);
			settings.setImageResource(R.drawable.action_settings);
			settings.setOnClickListener(this);
			addView(settings);
		}
	}

	public void onClick(View view) {
		getContext().startActivity(new Intent(ACTION_BLUETOOTH_SETTINGS));
	}
}
