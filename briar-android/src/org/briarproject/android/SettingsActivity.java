package org.briarproject.android;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.Resources;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.util.StringUtils;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.graphics.Typeface.DEFAULT_BOLD;
import static android.media.RingtoneManager.ACTION_RINGTONE_PICKER;
import static android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI;
import static android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI;
import static android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI;
import static android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT;
import static android.media.RingtoneManager.EXTRA_RINGTONE_TITLE;
import static android.media.RingtoneManager.EXTRA_RINGTONE_TYPE;
import static android.media.RingtoneManager.TYPE_NOTIFICATION;
import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static android.view.Gravity.CENTER;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.TestingConstants.SHOW_TESTING_ACTIVITY;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

public class SettingsActivity extends BriarActivity implements EventListener,
OnClickListener {

	public static final int REQUEST_RINGTONE = 2;

	private static final Logger LOG =
			Logger.getLogger(SettingsActivity.class.getName());

	private ScrollView scroll = null;
	private TextView enableBluetooth = null, enableBluetoothHint = null;
	private CheckBox notifyPrivateMessages = null, notifyForumPosts = null;
	private CheckBox notifyVibration = null;
	private TextView notifySound = null, notifySoundHint = null;
	private ListLoadingProgressBar progress = null;
	private ImageButton testingButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject private volatile EventBus eventBus;
	private volatile Settings settings;
	private volatile boolean bluetoothSetting = true;

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
		notifyPrivateMessages.setOnClickListener(this);
		settings.addView(notifyPrivateMessages);

		settings.addView(new FixedVerticalSpace(this));
		settings.addView(new HorizontalBorder(this));
		settings.addView(new FixedVerticalSpace(this));

		notifyForumPosts = new CheckBox(this);
		notifyForumPosts.setTextSize(18);
		notifyForumPosts.setText(R.string.notify_forum_posts_setting);
		notifyForumPosts.setOnClickListener(this);
		settings.addView(notifyForumPosts);

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

		if (SHOW_TESTING_ACTIVITY) {
			LinearLayout footer = new LinearLayout(this);
			footer.setLayoutParams(MATCH_WRAP);
			footer.setGravity(CENTER);
			int background = res.getColor(R.color.button_bar_background);
			footer.setBackgroundColor(background);
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
		eventBus.addListener(this);
		loadSettings();
	}

	private void loadSettings() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					TransportConfig c = db.getConfig(new TransportId("bt"));
					settings = db.getSettings();
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading settings took " + duration + " ms");
					bluetoothSetting = c.getBoolean("enable", false);
					displaySettings();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displaySettings() {
		runOnUiThread(new Runnable() {
			public void run() {
				scroll.setVisibility(VISIBLE);
				progress.setVisibility(GONE);

				int resId;
				if (bluetoothSetting) resId = R.string.bluetooth_setting_enabled;
				else resId = R.string.bluetooth_setting_disabled;
				enableBluetoothHint.setText(resId);

				notifyPrivateMessages.setChecked(settings.getBoolean(
						"notifyPrivateMessages", true));

				notifyForumPosts.setChecked(settings.getBoolean(
						"notifyForumPosts", true));

				notifyVibration.setChecked(settings.getBoolean(
						"notifyVibration", true));

				String text;
				if (settings.getBoolean("notifySound", true)) {
					String ringtoneName = settings.get("notifyRingtoneName");
					if (StringUtils.isNullOrEmpty(ringtoneName))
						text = getString(R.string.notify_sound_setting_default);
					else text = ringtoneName;
				} else {
					text = getString(R.string.notify_sound_setting_disabled);
				}
				notifySoundHint.setText(text);
			}
		});
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
	}

	public void onClick(View view) {
		if (progress == null) return; // Not created yet
		if (view == testingButton) {
			startActivity(new Intent(this, TestingActivity.class));
		} else if (view == enableBluetooth || view == enableBluetoothHint) {
			bluetoothSetting = !bluetoothSetting;
			BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			if (adapter != null) {
				if (bluetoothSetting) adapter.enable();
				else adapter.disable();
			}
			storeBluetoothSetting();
			displaySettings();
		} else if (view == notifyPrivateMessages) {
			Settings s = new Settings();
			s.putBoolean("notifyPrivateMessages",
					notifyPrivateMessages.isChecked());
			storeSettings(s);
		} else if (view == notifyForumPosts) {
			Settings s = new Settings();
			s.putBoolean("notifyForumPosts", notifyForumPosts.isChecked());
			storeSettings(s);
		} else if (view == notifyVibration) {
			Settings s = new Settings();
			s.putBoolean("notifyVibration", notifyVibration.isChecked());
			storeSettings(s);
		} else if (view == notifySound || view == notifySoundHint) {
			String title = getString(R.string.choose_ringtone_title);
			Intent i = new Intent(ACTION_RINGTONE_PICKER);
			i.putExtra(EXTRA_RINGTONE_TYPE, TYPE_NOTIFICATION);
			i.putExtra(EXTRA_RINGTONE_TITLE, title);
			i.putExtra(EXTRA_RINGTONE_DEFAULT_URI, DEFAULT_NOTIFICATION_URI);
			i.putExtra(EXTRA_RINGTONE_SHOW_SILENT, true);
			if (settings.getBoolean("notifySound", true)) {
				Uri uri;
				String ringtoneUri = settings.get("notifyRingtoneUri");
				if (StringUtils.isNullOrEmpty(ringtoneUri))
					uri = DEFAULT_NOTIFICATION_URI;
				else uri = Uri.parse(ringtoneUri);
				i.putExtra(EXTRA_RINGTONE_EXISTING_URI, uri);
			}
			this.startActivityForResult(i, REQUEST_RINGTONE);
		}
	}

	private void storeBluetoothSetting() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					TransportConfig c = new TransportConfig();
					c.putBoolean("enable", bluetoothSetting);
					long now = System.currentTimeMillis();
					db.mergeConfig(new TransportId("bt"), c);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Merging config took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void storeSettings(final Settings settings) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					db.mergeSettings(settings);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Merging settings took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_RINGTONE && result == RESULT_OK) {
			Settings s = new Settings();
			Uri uri = data.getParcelableExtra(EXTRA_RINGTONE_PICKED_URI);
			if (uri == null) {
				// The user chose silence
				notifySoundHint.setText(R.string.notify_sound_setting_disabled);
				s.putBoolean("notifySound", false);
				s.put("notifyRingtoneName", "");
				s.put("notifyRingtoneUri", "");
			} else if (RingtoneManager.isDefault(uri)) {
				// The user chose the default
				notifySoundHint.setText(R.string.notify_sound_setting_default);
				s.putBoolean("notifySound", true);
				s.put("notifyRingtoneName", "");
				s.put("notifyRingtoneUri", "");
			} else {
				// The user chose a ringtone other than the default
				Ringtone r = RingtoneManager.getRingtone(this, uri);
				String name = r.getTitle(this);
				notifySoundHint.setText(name);
				s.putBoolean("notifySound", true);
				s.put("notifyRingtoneName", name);
				s.put("notifyRingtoneUri", uri.toString());
			}
			storeSettings(s);
		}
	}

	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			LOG.info("Settings updated");
			loadSettings();
		}
	}
}
