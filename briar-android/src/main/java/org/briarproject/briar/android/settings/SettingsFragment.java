package org.briarproject.briar.android.settings;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.text.TextUtilsCompat;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceGroup;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.acra.ACRA;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.android.util.UserFeedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.media.RingtoneManager.ACTION_RINGTONE_PICKER;
import static android.media.RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI;
import static android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI;
import static android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI;
import static android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT;
import static android.media.RingtoneManager.EXTRA_RINGTONE_TITLE;
import static android.media.RingtoneManager.EXTRA_RINGTONE_TYPE;
import static android.media.RingtoneManager.TYPE_NOTIFICATION;
import static android.os.Build.VERSION.SDK_INT;
import static android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS;
import static android.provider.Settings.EXTRA_APP_PACKAGE;
import static android.provider.Settings.EXTRA_CHANNEL_ID;
import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static android.support.v4.view.ViewCompat.LAYOUT_DIRECTION_LTR;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PREF_BT_ENABLE;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_ALWAYS;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.android.TestingConstants.FEATURE_FLAG_DARK_THEME;
import static org.briarproject.briar.android.TestingConstants.FEATURE_FLAG_SIGN_IN_REMINDER;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_RINGTONE;
import static org.briarproject.briar.android.navdrawer.NavDrawerActivity.INTENT_SIGN_OUT;
import static org.briarproject.briar.api.android.AndroidNotificationManager.BLOG_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.CONTACT_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.FORUM_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.GROUP_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_BLOG;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_FORUM;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_GROUP;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_LOCK_SCREEN;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_PRIVATE;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_RINGTONE_NAME;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_RINGTONE_URI;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_SOUND;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_VIBRATION;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SettingsFragment extends PreferenceFragmentCompat
		implements EventListener, Preference.OnPreferenceChangeListener {

	public static final String SETTINGS_NAMESPACE = "android-ui";
	public static final String BT_NAMESPACE = BluetoothConstants.ID.getString();
	public static final String TOR_NAMESPACE = TorConstants.ID.getString();
	public static final String LANGUAGE = "pref_key_language";
	public static final String NOTIFY_SIGN_IN = "pref_key_notify_sign_in";

	private static final Logger LOG =
			Logger.getLogger(SettingsFragment.class.getName());

	private SettingsActivity listener;
	private ListPreference language;
	private ListPreference enableBluetooth;
	private ListPreference torNetwork;
	private CheckBoxPreference notifyPrivateMessages;
	private CheckBoxPreference notifyGroupMessages;
	private CheckBoxPreference notifyForumPosts;
	private CheckBoxPreference notifyBlogPosts;
	private CheckBoxPreference notifyVibration;
	private CheckBoxPreference notifyLockscreen;

	private Preference notifySound;

	// Fields that are accessed from background threads must be volatile
	volatile Settings settings;
	@Inject
	volatile SettingsManager settingsManager;
	@Inject
	volatile EventBus eventBus;

	@Inject
	AndroidExecutor androidExecutor;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (SettingsActivity) context;
		// we need to inject here,
		// because onActivityCreated() is called after onCreatePreferences()
		listener.getActivityComponent().inject(this);
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.settings);

		language = (ListPreference) findPreference(LANGUAGE);
		setLanguageEntries();
		ListPreference theme =
				(ListPreference) findPreference("pref_key_theme");
		enableBluetooth = (ListPreference) findPreference("pref_key_bluetooth");
		torNetwork = (ListPreference) findPreference("pref_key_tor_network");
		CheckBoxPreference notifySignIn =
				(CheckBoxPreference) findPreference(NOTIFY_SIGN_IN);
		notifyPrivateMessages = (CheckBoxPreference) findPreference(
				"pref_key_notify_private_messages");
		notifyGroupMessages = (CheckBoxPreference) findPreference(
				"pref_key_notify_group_messages");
		notifyForumPosts = (CheckBoxPreference) findPreference(
				"pref_key_notify_forum_posts");
		notifyBlogPosts = (CheckBoxPreference) findPreference(
				"pref_key_notify_blog_posts");
		notifyVibration = (CheckBoxPreference) findPreference(
				"pref_key_notify_vibration");
		notifyLockscreen = (CheckBoxPreference) findPreference(
				"pref_key_notify_lock_screen");
		notifySound = findPreference("pref_key_notify_sound");

		setSettingsEnabled(false);

		language.setOnPreferenceChangeListener(this);
		theme.setOnPreferenceChangeListener((preference, newValue) -> {
			if (getActivity() != null) {
				// activate new theme
				UiUtils.setTheme(getActivity(), (String) newValue);
				// bring up parent activity, so it can change its theme as well
				// upstream bug: https://issuetracker.google.com/issues/38352704
				Intent intent =
						new Intent(getActivity(), NavDrawerActivity.class);
				intent.setFlags(
						FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
				// bring this activity back to the foreground
				intent = new Intent(getActivity(), getActivity().getClass());
				startActivity(intent);
				getActivity().finish();
			}
			return true;
		});
		enableBluetooth.setOnPreferenceChangeListener(this);
		torNetwork.setOnPreferenceChangeListener(this);
		if (SDK_INT >= 21) {
			notifyLockscreen.setVisible(true);
			notifyLockscreen.setOnPreferenceChangeListener(this);
		}

		findPreference("pref_key_send_feedback").setOnPreferenceClickListener(
				preference -> {
					triggerFeedback();
					return true;
				});

		if (IS_DEBUG_BUILD) {
			findPreference("pref_key_explode").setOnPreferenceClickListener(
					preference -> {
						throw new RuntimeException("Boom!");
					}
			);
		} else {
			theme.setVisible(FEATURE_FLAG_DARK_THEME);
			notifySignIn.setVisible(FEATURE_FLAG_SIGN_IN_REMINDER);

			findPreference("pref_key_explode").setVisible(false);
			findPreference("pref_key_test_data").setVisible(false);
			PreferenceGroup testing =
					findPreference("pref_key_explode").getParent();
			if (testing == null) throw new AssertionError();
			testing.setVisible(false);
		}

		loadSettings();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);
		ColorDrawable divider = new ColorDrawable(
				ContextCompat.getColor(getContext(), R.color.divider));
		setDivider(divider);
		return view;
	}

	@Override
	public void onStart() {
		super.onStart();
		eventBus.addListener(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		eventBus.removeListener(this);
	}

	private void setLanguageEntries() {
		CharSequence[] tags = language.getEntryValues();
		List<CharSequence> entries = new ArrayList<>(tags.length);
		List<CharSequence> entryValues = new ArrayList<>(tags.length);
		for (CharSequence cs : tags) {
			String tag = cs.toString();
			if (tag.equals("default")) {
				entries.add(getString(R.string.pref_language_default));
				entryValues.add(tag);
				continue;
			}
			Locale locale = Localizer.getLocaleFromTag(tag);
			if (locale == null)
				throw new IllegalStateException();
			// Exclude RTL locales on API < 17, they won't be laid out correctly
			if (SDK_INT < 17 && !isLeftToRight(locale)) {
				if (LOG.isLoggable(INFO))
					LOG.info("Skipping RTL locale " + tag);
				continue;
			}
			String nativeName = locale.getDisplayName(locale);
			// Fallback to English if the name is unknown in both native and
			// current locale.
			if (nativeName.equals(tag)) {
				String tmp = locale.getDisplayLanguage(Locale.ENGLISH);
				if (!tmp.isEmpty() && !tmp.equals(nativeName))
					nativeName = tmp;
			}
			// Prefix with LRM marker to prevent any RTL direction
			entries.add("\u200E" + nativeName.substring(0, 1).toUpperCase()
					+ nativeName.substring(1));
			entryValues.add(tag);
		}
		language.setEntries(entries.toArray(new CharSequence[0]));
		language.setEntryValues(entryValues.toArray(new CharSequence[0]));
	}

	private boolean isLeftToRight(Locale locale) {
		// TextUtilsCompat returns the wrong direction for Hebrew on some phones
		String language = locale.getLanguage();
		if (language.equals("iw") || language.equals("he")) return false;
		int direction = TextUtilsCompat.getLayoutDirectionFromLocale(locale);
		return direction == LAYOUT_DIRECTION_LTR;
	}

	private void loadSettings() {
		listener.runOnDbThread(() -> {
			try {
				long start = now();
				settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
				Settings btSettings = settingsManager.getSettings(BT_NAMESPACE);
				Settings torSettings =
						settingsManager.getSettings(TOR_NAMESPACE);
				logDuration(LOG, "Loading settings", start);
				boolean btSetting =
						btSettings.getBoolean(PREF_BT_ENABLE, false);
				int torSetting = torSettings.getInt(PREF_TOR_NETWORK,
						PREF_TOR_NETWORK_ALWAYS);
				displaySettings(btSetting, torSetting);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void displaySettings(boolean btSetting, int torSetting) {
		listener.runOnUiThreadUnlessDestroyed(() -> {
			enableBluetooth.setValue(Boolean.toString(btSetting));
			torNetwork.setValue(Integer.toString(torSetting));

			if (SDK_INT < 26) {
				notifyPrivateMessages.setChecked(settings.getBoolean(
						PREF_NOTIFY_PRIVATE, true));
				notifyGroupMessages.setChecked(settings.getBoolean(
						PREF_NOTIFY_GROUP, true));
				notifyForumPosts.setChecked(settings.getBoolean(
						PREF_NOTIFY_FORUM, true));
				notifyBlogPosts.setChecked(settings.getBoolean(
						PREF_NOTIFY_BLOG, true));
				notifyVibration.setChecked(settings.getBoolean(
						PREF_NOTIFY_VIBRATION, true));
				notifyPrivateMessages.setOnPreferenceChangeListener(this);
				notifyGroupMessages.setOnPreferenceChangeListener(this);
				notifyForumPosts.setOnPreferenceChangeListener(this);
				notifyBlogPosts.setOnPreferenceChangeListener(this);
				notifyVibration.setOnPreferenceChangeListener(this);
				notifyLockscreen.setChecked(settings.getBoolean(
						PREF_NOTIFY_LOCK_SCREEN, false));
				notifySound.setOnPreferenceClickListener(
						pref -> onNotificationSoundClicked());
				String text;
				if (settings.getBoolean(PREF_NOTIFY_SOUND, true)) {
					String ringtoneName =
							settings.get(PREF_NOTIFY_RINGTONE_NAME);
					if (StringUtils.isNullOrEmpty(ringtoneName)) {
						text = getString(R.string.notify_sound_setting_default);
					} else {
						text = ringtoneName;
					}
				} else {
					text = getString(R.string.notify_sound_setting_disabled);
				}
				notifySound.setSummary(text);
			} else {
				setupNotificationPreference(notifyPrivateMessages,
						CONTACT_CHANNEL_ID,
						R.string.notify_private_messages_setting_summary_26);
				setupNotificationPreference(notifyGroupMessages,
						GROUP_CHANNEL_ID,
						R.string.notify_group_messages_setting_summary_26);
				setupNotificationPreference(notifyForumPosts, FORUM_CHANNEL_ID,
						R.string.notify_forum_posts_setting_summary_26);
				setupNotificationPreference(notifyBlogPosts, BLOG_CHANNEL_ID,
						R.string.notify_blog_posts_setting_summary_26);
				notifyVibration.setVisible(false);
				notifyLockscreen.setVisible(false);
				notifySound.setVisible(false);
			}
			setSettingsEnabled(true);
		});
	}

	private void setSettingsEnabled(boolean enabled) {
		// preferences not needed here, because handled by SharedPreferences:
		// - pref_key_theme
		// - pref_key_notify_sign_in
		enableBluetooth.setEnabled(enabled);
		torNetwork.setEnabled(enabled);
		notifyPrivateMessages.setEnabled(enabled);
		notifyGroupMessages.setEnabled(enabled);
		notifyForumPosts.setEnabled(enabled);
		notifyBlogPosts.setEnabled(enabled);
		notifyVibration.setEnabled(enabled);
		notifyLockscreen.setEnabled(enabled);
		notifySound.setEnabled(enabled);
	}

	@TargetApi(26)
	private void setupNotificationPreference(CheckBoxPreference pref,
			String channelId, @StringRes int summary) {
		pref.setWidgetLayoutResource(0);
		pref.setSummary(summary);
		pref.setOnPreferenceClickListener(clickedPref -> {
			Intent intent = new Intent(ACTION_CHANNEL_NOTIFICATION_SETTINGS)
					.putExtra(EXTRA_APP_PACKAGE, getContext().getPackageName())
					.putExtra(EXTRA_CHANNEL_ID, channelId);
			startActivity(intent);
			return true;
		});
	}

	private boolean onNotificationSoundClicked() {
		String title = getString(R.string.choose_ringtone_title);
		Intent i = new Intent(ACTION_RINGTONE_PICKER);
		i.putExtra(EXTRA_RINGTONE_TYPE, TYPE_NOTIFICATION);
		i.putExtra(EXTRA_RINGTONE_TITLE, title);
		i.putExtra(EXTRA_RINGTONE_DEFAULT_URI,
				DEFAULT_NOTIFICATION_URI);
		i.putExtra(EXTRA_RINGTONE_SHOW_SILENT, true);
		if (settings.getBoolean(PREF_NOTIFY_SOUND, true)) {
			Uri uri;
			String ringtoneUri =
					settings.get(PREF_NOTIFY_RINGTONE_URI);
			if (StringUtils.isNullOrEmpty(ringtoneUri))
				uri = DEFAULT_NOTIFICATION_URI;
			else uri = Uri.parse(ringtoneUri);
			i.putExtra(EXTRA_RINGTONE_EXISTING_URI, uri);
		}
		startActivityForResult(i, REQUEST_RINGTONE);
		return true;
	}

	private void triggerFeedback() {
		androidExecutor.runOnBackgroundThread(() -> ACRA.getErrorReporter()
				.handleException(new UserFeedback(), false));
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference == language) {
			if (!language.getValue().equals(newValue))
				languageChanged((String) newValue);
			return false;
		} else if (preference == enableBluetooth) {
			boolean btSetting = Boolean.valueOf((String) newValue);
			storeBluetoothSettings(btSetting);
		} else if (preference == torNetwork) {
			int torSetting = Integer.valueOf((String) newValue);
			storeTorSettings(torSetting);
		} else if (preference == notifyPrivateMessages) {
			Settings s = new Settings();
			s.putBoolean(PREF_NOTIFY_PRIVATE, (Boolean) newValue);
			storeSettings(s);
		} else if (preference == notifyGroupMessages) {
			Settings s = new Settings();
			s.putBoolean(PREF_NOTIFY_GROUP, (Boolean) newValue);
			storeSettings(s);
		} else if (preference == notifyForumPosts) {
			Settings s = new Settings();
			s.putBoolean(PREF_NOTIFY_FORUM, (Boolean) newValue);
			storeSettings(s);
		} else if (preference == notifyBlogPosts) {
			Settings s = new Settings();
			s.putBoolean(PREF_NOTIFY_BLOG, (Boolean) newValue);
			storeSettings(s);
		} else if (preference == notifyVibration) {
			Settings s = new Settings();
			s.putBoolean(PREF_NOTIFY_VIBRATION, (Boolean) newValue);
			storeSettings(s);
		} else if (preference == notifyLockscreen) {
			Settings s = new Settings();
			s.putBoolean(PREF_NOTIFY_LOCK_SCREEN, (Boolean) newValue);
			storeSettings(s);
		}
		return true;
	}

	private void languageChanged(String newValue) {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.pref_language_title);
		builder.setMessage(R.string.pref_language_changed);
		builder.setPositiveButton(R.string.sign_out_button,
				(dialogInterface, i) -> {
					language.setValue(newValue);
					Intent intent = new Intent(getContext(),
							NavDrawerActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.putExtra(INTENT_SIGN_OUT, true);
					getActivity().startActivity(intent);
					getActivity().finish();
				});
		builder.setNegativeButton(R.string.cancel, null);
		builder.setCancelable(false);
		builder.show();
	}

	private void storeTorSettings(int torSetting) {
		listener.runOnDbThread(() -> {
			try {
				Settings s = new Settings();
				s.putInt(PREF_TOR_NETWORK, torSetting);
				long start = now();
				settingsManager.mergeSettings(s, TOR_NAMESPACE);
				logDuration(LOG, "Merging settings", start);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void storeBluetoothSettings(boolean btSetting) {
		listener.runOnDbThread(() -> {
			try {
				Settings s = new Settings();
				s.putBoolean(PREF_BT_ENABLE, btSetting);
				long start = now();
				settingsManager.mergeSettings(s, BT_NAMESPACE);
				logDuration(LOG, "Merging settings", start);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void storeSettings(Settings settings) {
		listener.runOnDbThread(() -> {
			try {
				long start = now();
				settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);
				logDuration(LOG, "Merging settings", start);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
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
				s.putBoolean(PREF_NOTIFY_SOUND, false);
				s.put(PREF_NOTIFY_RINGTONE_NAME, "");
				s.put(PREF_NOTIFY_RINGTONE_URI, "");
			} else if (RingtoneManager.isDefault(uri)) {
				// The user chose the default
				s.putBoolean(PREF_NOTIFY_SOUND, true);
				s.put(PREF_NOTIFY_RINGTONE_NAME, "");
				s.put(PREF_NOTIFY_RINGTONE_URI, "");
			} else {
				// The user chose a ringtone other than the default
				Ringtone r = RingtoneManager.getRingtone(getContext(), uri);
				if (r == null) {
					Toast.makeText(getContext(), R.string.cannot_load_ringtone,
							LENGTH_SHORT).show();
				} else {
					String name = r.getTitle(getContext());
					s.putBoolean(PREF_NOTIFY_SOUND, true);
					s.put(PREF_NOTIFY_RINGTONE_NAME, name);
					s.put(PREF_NOTIFY_RINGTONE_URI, uri.toString());
				}
			}
			storeSettings(s);
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			String namespace = ((SettingsUpdatedEvent) e).getNamespace();
			if (namespace.equals(BT_NAMESPACE)
					|| namespace.equals(TOR_NAMESPACE)
					|| namespace.equals(SETTINGS_NAMESPACE)) {
				LOG.info("Settings updated");
				loadSettings();
			}
		}
	}

}
