package org.briarproject.android.fragment;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.Resources;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.TestingActivity;
import org.briarproject.android.panic.PanicPreferencesActivity;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.FixedVerticalSpace;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.settings.SettingsManager;
import org.briarproject.util.StringUtils;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.app.Activity.RESULT_OK;
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

public class SettingsFragment extends BaseEventFragment implements
		View.OnClickListener {

	public static final String TAG = "SettingsFragment";
	public static final int REQUEST_RINGTONE = 2;
	public static final String SETTINGS_NAMESPACE = "android-ui";

	private static final Logger LOG =
			Logger.getLogger(SettingsFragment.class.getName());

	private ScrollView scroll = null;
	private TextView enableBluetooth = null, enableBluetoothHint = null;
	private CheckBox notifyPrivateMessages = null, notifyForumPosts = null;
	private CheckBox notifyVibration = null;
	private TextView torOverWifi = null, torOverWifiHint = null;
	private TextView panicSettings = null, panicSettingsHint = null;
	private TextView notifySound = null, notifySoundHint = null;
	private ListLoadingProgressBar progress = null;
	private ImageButton testingButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile SettingsManager settingsManager;
	private volatile Settings settings;
	private volatile boolean bluetoothSetting = false, torSetting = false;

	public static SettingsFragment newInstance() {

		Bundle args = new Bundle();

		SettingsFragment fragment = new SettingsFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		LinearLayout layout = new LinearLayout(getContext());
		layout.setOrientation(VERTICAL);

		scroll = new ScrollView(getContext());

		LinearLayout settings = new LinearLayout(getContext());
		settings.setOrientation(VERTICAL);
		int pad = LayoutUtils.getPadding(getContext());
		settings.setPadding(pad, pad, pad, pad);

		TextView bluetoothTitle = new TextView(getContext());
		bluetoothTitle.setPadding(pad, 0, pad, 0);
		bluetoothTitle.setTypeface(DEFAULT_BOLD);
		Resources res = getResources();
		int titleText = res.getColor(R.color.settings_title_text);
		bluetoothTitle.setTextColor(titleText);
		bluetoothTitle.setText(R.string.bluetooth_setting_title);
		settings.addView(bluetoothTitle);

		HorizontalBorder underline = new HorizontalBorder(getContext());
		int titleUnderline = res.getColor(R.color.settings_title_underline);
		underline.setBackgroundColor(titleUnderline);
		settings.addView(underline);

		enableBluetooth = new TextView(getContext());
		enableBluetooth.setPadding(pad, pad, pad, 0);
		enableBluetooth.setTextSize(18);
		enableBluetooth.setText(R.string.bluetooth_setting);
		enableBluetooth.setOnClickListener(this);
		settings.addView(enableBluetooth);

		enableBluetoothHint = new TextView(getContext());
		enableBluetoothHint.setPadding(pad, 0, pad, pad);
		enableBluetoothHint.setOnClickListener(this);
		settings.addView(enableBluetoothHint);

		TextView torTitle = new TextView(getContext());
		torTitle.setPadding(pad, 0, pad, 0);
		torTitle.setTypeface(DEFAULT_BOLD);
		torTitle.setTextColor(titleText);
		torTitle.setText(R.string.tor_wifi_setting_title);
		settings.addView(torTitle);

		underline = new HorizontalBorder(getContext());
		underline.setBackgroundColor(titleUnderline);
		settings.addView(underline);

		torOverWifi = new TextView(getContext());
		torOverWifi.setPadding(pad, pad, pad, 0);
		torOverWifi.setTextSize(18);
		torOverWifi.setText(R.string.tor_wifi_setting);
		torOverWifi.setOnClickListener(this);
		settings.addView(torOverWifi);

		torOverWifiHint = new TextView(getContext());
		torOverWifiHint.setPadding(pad, 0, pad, pad);
		torOverWifiHint.setOnClickListener(this);
		settings.addView(torOverWifiHint);

		TextView panicTitle = new TextView(getContext());
		panicTitle.setPadding(pad, 0, pad, 0);
		panicTitle.setTypeface(DEFAULT_BOLD);
		panicTitle.setTextColor(titleText);
		panicTitle.setText(R.string.panic_setting_title);
		settings.addView(panicTitle);

		underline = new HorizontalBorder(getContext());
		underline.setBackgroundColor(titleUnderline);
		settings.addView(underline);

		panicSettings = new TextView(getContext());
		panicSettings.setPadding(pad, pad, pad, 0);
		panicSettings.setTextSize(18);
		panicSettings.setText(R.string.panic_setting);
		panicSettings.setOnClickListener(this);
		settings.addView(panicSettings);

		panicSettingsHint = new TextView(getContext());
		panicSettingsHint.setText(R.string.panic_setting_hint);
		panicSettingsHint.setPadding(pad, 0, pad, pad);
		panicSettingsHint.setOnClickListener(this);
		settings.addView(panicSettingsHint);

		TextView notificationsTitle = new TextView(getContext());
		notificationsTitle.setPadding(pad, 0, pad, 0);
		notificationsTitle.setTypeface(DEFAULT_BOLD);
		notificationsTitle.setTextColor(titleText);
		notificationsTitle.setText(R.string.notification_settings_title);
		settings.addView(notificationsTitle);

		underline = new HorizontalBorder(getContext());
		underline.setBackgroundColor(titleUnderline);
		settings.addView(underline);

		settings.addView(new FixedVerticalSpace(getContext()));

		notifyPrivateMessages = new CheckBox(getContext());
		notifyPrivateMessages.setTextSize(18);
		notifyPrivateMessages.setText(R.string.notify_private_messages_setting);
		notifyPrivateMessages.setOnClickListener(this);
		settings.addView(notifyPrivateMessages);

		settings.addView(new FixedVerticalSpace(getContext()));
		settings.addView(new HorizontalBorder(getContext()));
		settings.addView(new FixedVerticalSpace(getContext()));

		notifyForumPosts = new CheckBox(getContext());
		notifyForumPosts.setTextSize(18);
		notifyForumPosts.setText(R.string.notify_forum_posts_setting);
		notifyForumPosts.setOnClickListener(this);
		settings.addView(notifyForumPosts);

		settings.addView(new FixedVerticalSpace(getContext()));
		settings.addView(new HorizontalBorder(getContext()));
		settings.addView(new FixedVerticalSpace(getContext()));

		notifyVibration = new CheckBox(getContext());
		notifyVibration.setTextSize(18);
		notifyVibration.setText(R.string.notify_vibration_setting);
		notifyVibration.setOnClickListener(this);
		settings.addView(notifyVibration);

		settings.addView(new FixedVerticalSpace(getContext()));
		settings.addView(new HorizontalBorder(getContext()));

		notifySound = new TextView(getContext());
		notifySound.setPadding(pad, pad, pad, 0);
		notifySound.setTextSize(18);
		notifySound.setText(R.string.notify_sound_setting);
		notifySound.setOnClickListener(this);
		settings.addView(notifySound);

		notifySoundHint = new TextView(getContext());
		notifySoundHint.setPadding(pad, 0, pad, pad);
		notifySoundHint.setOnClickListener(this);
		settings.addView(notifySoundHint);

		settings.addView(new HorizontalBorder(getContext()));

		scroll.addView(settings);
		scroll.setLayoutParams(MATCH_WRAP_1);
		scroll.setVisibility(GONE);
		layout.addView(scroll);

		progress = new ListLoadingProgressBar(getContext());
		layout.addView(progress);

		layout.addView(new HorizontalBorder(getContext()));

		if (SHOW_TESTING_ACTIVITY) {
			LinearLayout footer = new LinearLayout(getContext());
			footer.setLayoutParams(MATCH_WRAP);
			footer.setGravity(CENTER);
			int background = res.getColor(R.color.button_bar_background);
			footer.setBackgroundColor(background);
			testingButton = new ImageButton(getContext());
			testingButton.setBackgroundResource(0);
			testingButton.setImageResource(R.drawable.action_about);
			testingButton.setOnClickListener(this);
			footer.addView(testingButton);
			layout.addView(footer);
		}

		return layout;
	}

	@Override
	public void onResume() {
		super.onResume();
		loadSettings();
	}

	private void loadSettings() {
		listener.runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
					Settings btSettings = settingsManager.getSettings("bt");
					Settings torSettings = settingsManager.getSettings("tor");
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading settings took " + duration + " ms");
					bluetoothSetting = btSettings.getBoolean("enable", false);
					torSetting = torSettings.getBoolean("torOverWifi", false);
					displaySettings();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displaySettings() {
		listener.runOnUiThread(new Runnable() {
			public void run() {
				scroll.setVisibility(VISIBLE);
				progress.setVisibility(GONE);

				int resId;
				if (bluetoothSetting)
					resId = R.string.bluetooth_setting_enabled;
				else resId = R.string.bluetooth_setting_disabled;
				enableBluetoothHint.setText(resId);

				if (torSetting) resId = R.string.tor_wifi_setting_enabled;
				else resId = R.string.tor_wifi_setting_disabled;
				torOverWifiHint.setText(resId);

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

	public void onClick(View view) {
		if (progress == null) return; // Not created yet
		if (view == testingButton) {
			startActivity(new Intent(getActivity(), TestingActivity.class));
		} else if (view == enableBluetooth || view == enableBluetoothHint) {
			bluetoothSetting = !bluetoothSetting;
			BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			if (adapter != null) {
				AndroidUtils.setBluetooth(adapter, bluetoothSetting);
			}
			storeBluetoothSettings();
			displaySettings();
		} else if (view == torOverWifi || view == torOverWifiHint) {
			torSetting = !torSetting;
			storeTorSettings();
			displaySettings();
		} else if (view == notifyPrivateMessages) {
			Settings s = new Settings();
			s.putBoolean("notifyPrivateMessages",
					notifyPrivateMessages.isChecked());
			storeSettings(s);
		} else if (view == panicSettings || view == panicSettingsHint) {
			startActivity(new Intent(getActivity(),
					PanicPreferencesActivity.class));
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

	private void storeTorSettings() {
		listener.runOnDbThread(new Runnable() {
			public void run() {
				try {
					Settings s = new Settings();
					s.putBoolean("torOverWifi", torSetting);
					long now = System.currentTimeMillis();
					settingsManager.mergeSettings(s, "tor");
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

	private void storeBluetoothSettings() {
		listener.runOnDbThread(new Runnable() {
			public void run() {
				try {
					Settings s = new Settings();
					s.putBoolean("enable", bluetoothSetting);
					long now = System.currentTimeMillis();
					settingsManager.mergeSettings(s, "bt");
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

	private void storeSettings(final Settings settings) {
		listener.runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);
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
				Ringtone r = RingtoneManager.getRingtone(getContext(), uri);
				String name = r.getTitle(getContext());
				notifySoundHint.setText(name);
				s.putBoolean("notifySound", true);
				s.put("notifyRingtoneName", name);
				s.put("notifyRingtoneUri", uri.toString());
			}
			storeSettings(s);
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			String namespace = ((SettingsUpdatedEvent) e).getNamespace();
			if (namespace.equals("bt") || namespace.equals("tor")
					|| namespace.equals(SETTINGS_NAMESPACE)) {
				LOG.info("Settings updated");
				loadSettings();
			}
		}
	}
}
