package org.briarproject.android;

import static android.view.Gravity.CENTER;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends BriarActivity implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(SettingsActivity.class.getName());

	private CheckBox bluetooth = null;
	private ScrollView scroll = null;
	private ListLoadingProgressBar progress = null;
	private ImageButton testingButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(VERTICAL);

		scroll = new ScrollView(this);

		LinearLayout settings = new LinearLayout(this);
		settings.setOrientation(VERTICAL);
		int pad = LayoutUtils.getPadding(this);
		settings.setPadding(pad, pad, pad, pad);

		bluetooth = new CheckBox(this);
		bluetooth.setLayoutParams(MATCH_WRAP);
		bluetooth.setTextSize(18);
		bluetooth.setText(R.string.activate_bluetooth_option);
		bluetooth.setOnClickListener(this);
		settings.addView(bluetooth);

		TextView bluetoothHint = new TextView(this);
		bluetoothHint.setText(R.string.activate_bluetooth_explanation);
		settings.addView(bluetoothHint);

		scroll.addView(settings);
		scroll.setLayoutParams(MATCH_WRAP_1);
		scroll.setVisibility(GONE);
		layout.addView(scroll);

		progress = new ListLoadingProgressBar(this);
		layout.addView(progress);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setGravity(CENTER);
		Resources res = getResources();
		footer.setBackgroundColor(res.getColor(R.color.button_bar_background));
		testingButton = new ImageButton(this);
		testingButton.setBackgroundResource(0);
		testingButton.setImageResource(R.drawable.action_about);
		testingButton.setOnClickListener(this);
		footer.addView(testingButton);
		layout.addView(footer);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		loadSettings();
	}

	private void loadSettings() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					boolean activateBluetooth = true;
					TransportConfig c = db.getConfig(new TransportId("bt"));
					if(c != null && "false".equals(c.get("enable")))
						activateBluetooth = false;
					displaySettings(activateBluetooth);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displaySettings(final boolean activateBluetooth) {
		runOnUiThread(new Runnable() {
			public void run() {
				scroll.setVisibility(VISIBLE);
				progress.setVisibility(GONE);
				bluetooth.setChecked(activateBluetooth);
			}
		});
	}

	public void onClick(View view) {
		if(testingButton == null) return; // Not created yet
		if(view == bluetooth) {
			boolean activateBluetooth = bluetooth.isChecked();
			if(!activateBluetooth) {
				BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
				if(adapter != null) adapter.disable();
			}
			storeSettings(activateBluetooth);
		} else if(view == testingButton) {
			startActivity(new Intent(this, TestingActivity.class));
		}
	}

	private void storeSettings(final boolean activateBluetooth) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					TransportConfig c = new TransportConfig();
					c.put("enable", String.valueOf(activateBluetooth));
					db.mergeConfig(new TransportId("bt"), c);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}
