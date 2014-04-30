package org.briarproject.android;

import static android.graphics.Typeface.DEFAULT_BOLD;
import static android.view.Gravity.CENTER;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.TestingConstants.SHOW_TESTING_ACTIVITY;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.util.FixedVerticalSpace;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.Settings;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.SettingsUpdatedEvent;

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

public class SettingsActivity extends BriarActivity implements EventListener,
OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(SettingsActivity.class.getName());

	private ScrollView scroll = null;
	private TextView enableBluetooth = null, enableBluetoothHint = null;
	private CheckBox notifyPrivateMessages = null, notifyGroupPosts = null;
	private CheckBox notifyVibration = null;
	private TextView notifySound = null, notifySoundHint = null;
	private ListLoadingProgressBar progress = null;
	private ImageButton testingButton = null;
	private boolean bluetoothSetting = true, soundSetting = true;

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

		TextView bluetoothTitle = new TextView(this);
		bluetoothTitle.setPadding(pad, 0, pad, 0);
		bluetoothTitle.setTypeface(DEFAULT_BOLD);
		Resources res = getResources();
		int titleText = res.getColor(R.color.settings_title_text);
		bluetoothTitle.setTextColor(titleText);
		bluetoothTitle.setText(R.string.bluetooth_setting_title);
		settings.addView(bluetoothTitle);

		HorizontalBorder underline = new HorizontalBorder(this);
		int titleUnderline = res.getColor(R.color.settings_title_underline);
		underline.setBackgroundColor(titleUnderline);
		settings.addView(underline);

		enableBluetooth = new TextView(this);
		enableBluetooth.setPadding(pad, pad, pad, 0);
		enableBluetooth.setTextSize(18);
		enableBluetooth.setText(R.string.bluetooth_setting);
		enableBluetooth.setOnClickListener(this);
		settings.addView(enableBluetooth);

		enableBluetoothHint = new TextView(this);
		enableBluetoothHint.setPadding(pad, 0, pad, pad);
		enableBluetoothHint.setText(R.string.bluetooth_setting_enabled);
		enableBluetoothHint.setOnClickListener(this);
		settings.addView(enableBluetoothHint);

		TextView notificationsTitle = new TextView(this);
		notificationsTitle.setPadding(pad, 0, pad, 0);
		notificationsTitle.setTypeface(DEFAULT_BOLD);
		notificationsTitle.setTextColor(titleText);
		notificationsTitle.setText(R.string.notification_settings_title);
		settings.addView(notificationsTitle);

		underline = new HorizontalBorder(this);
		underline.setBackgroundColor(titleUnderline);
		settings.addView(underline);

		settings.addView(new FixedVerticalSpace(this));

		notifyPrivateMessages = new CheckBox(this);
		notifyPrivateMessages.setTextSize(18);
		notifyPrivateMessages.setText(R.string.notify_private_messages_setting);
		notifyPrivateMessages.setChecked(true);
		notifyPrivateMessages.setOnClickListener(this);
		settings.addView(notifyPrivateMessages);

		settings.addView(new FixedVerticalSpace(this));
		settings.addView(new HorizontalBorder(this));
		settings.addView(new FixedVerticalSpace(this));

		notifyGroupPosts = new CheckBox(this);
		notifyGroupPosts.setTextSize(18);
		notifyGroupPosts.setText(R.string.notify_group_posts_setting);
		notifyGroupPosts.setChecked(true);
		notifyGroupPosts.setOnClickListener(this);
		settings.addView(notifyGroupPosts);

		settings.addView(new FixedVerticalSpace(this));
		settings.addView(new HorizontalBorder(this));
		settings.addView(new FixedVerticalSpace(this));

		notifyVibration = new CheckBox(this);
		notifyVibration.setTextSize(18);
		notifyVibration.setText(R.string.notify_vibration_setting);
		notifyVibration.setOnClickListener(this);
		settings.addView(notifyVibration);

		settings.addView(new FixedVerticalSpace(this));
		settings.addView(new HorizontalBorder(this));

		notifySound = new TextView(this);
		notifySound.setPadding(pad, pad, pad, 0);
		notifySound.setTextSize(18);
		notifySound.setText(R.string.notify_sound_setting);
		notifySound.setOnClickListener(this);
		settings.addView(notifySound);

		notifySoundHint = new TextView(this);
		notifySoundHint.setPadding(pad, 0, pad, pad);
		notifySoundHint.setText(R.string.notify_sound_setting_enabled);
		notifySoundHint.setOnClickListener(this);
		settings.addView(notifySoundHint);

		settings.addView(new HorizontalBorder(this));

		scroll.addView(settings);
		scroll.setLayoutParams(MATCH_WRAP_1);
		scroll.setVisibility(GONE);
		layout.addView(scroll);

		progress = new ListLoadingProgressBar(this);
		layout.addView(progress);

		layout.addView(new HorizontalBorder(this));

		if(SHOW_TESTING_ACTIVITY) {
			LinearLayout footer = new LinearLayout(this);
			footer.setLayoutParams(MATCH_WRAP);
			footer.setGravity(CENTER);
			footer.setBackgroundColor(res.getColor(R.color.button_bar_background));
			testingButton = new ImageButton(this);
			testingButton.setBackgroundResource(0);
			testingButton.setImageResource(R.drawable.action_about);
			testingButton.setOnClickListener(this);
			footer.addView(testingButton);
			layout.addView(footer);
		}

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		loadSettings();
	}

	private void loadSettings() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					TransportConfig c = db.getConfig(new TransportId("bt"));
					Settings settings = db.getSettings();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading settings took " + duration + " ms");
					boolean btSetting = c.getBoolean("enable", true);
					displaySettings(btSetting, settings);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displaySettings(final boolean btSetting,
			final Settings settings) {
		runOnUiThread(new Runnable() {
			public void run() {
				scroll.setVisibility(VISIBLE);
				progress.setVisibility(GONE);

				bluetoothSetting = btSetting;
				int resId;
				if(bluetoothSetting) resId = R.string.bluetooth_setting_enabled;
				else resId = R.string.bluetooth_setting_disabled;
				enableBluetoothHint.setText(resId);

				notifyPrivateMessages.setChecked(settings.getBoolean(
						"notifyPrivateMessages", true));

				notifyGroupPosts.setChecked(settings.getBoolean(
						"notifyGroupPosts", true));

				notifyVibration.setChecked(settings.getBoolean(
						"notifyVibration", true));

				soundSetting = settings.getBoolean("notifySound", true);
				if(soundSetting) resId = R.string.notify_sound_setting_enabled;
				else resId = R.string.notify_sound_setting_disabled;
				notifySoundHint.setText(resId);
			}
		});
	}

	@Override
	public void onPause() {
		super.onPause();
		db.removeListener(this);
	}

	public void onClick(View view) {
		if(progress == null) return; // Not created yet
		if(view == testingButton) {
			startActivity(new Intent(this, TestingActivity.class));
			return;
		}
		if(view == enableBluetooth || view == enableBluetoothHint) {
			bluetoothSetting = !bluetoothSetting;
			BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			if(adapter != null) {
				if(bluetoothSetting) adapter.enable();
				else adapter.disable();
			}
		} else if(view == notifySound || view == notifySoundHint) {
			soundSetting = !soundSetting;
		}
		Settings settings = new Settings();
		settings.putBoolean("notifyPrivateMessages",
				notifyPrivateMessages.isChecked());
		settings.putBoolean("notifyGroupPosts",
				notifyGroupPosts.isChecked());
		settings.putBoolean("notifyVibration",
				notifyVibration.isChecked());
		settings.putBoolean("notifySound", soundSetting);
		storeSettings(bluetoothSetting, settings);
	}

	private void storeSettings(final boolean btSetting,
			final Settings settings) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					TransportConfig c = new TransportConfig();
					c.putBoolean("enable", btSetting);
					long now = System.currentTimeMillis();
					db.mergeConfig(new TransportId("bt"), c);
					db.mergeSettings(settings);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Storing settings took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	public void eventOccurred(Event e) {
		if(e instanceof SettingsUpdatedEvent) {
			LOG.info("Settings updated");
			loadSettings();
		}
	}
}
