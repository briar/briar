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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.plugin.tor.CircumventionProvider;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.util.UiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.text.TextUtilsCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
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
import static android.widget.Toast.LENGTH_SHORT;
import static androidx.core.view.ViewCompat.LAYOUT_DIRECTION_LTR;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.plugin.Plugin.PREF_PLUGIN_ENABLE;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_MOBILE;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_AUTOMATIC;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_NEVER;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_ONLY_WHEN_CHARGING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.android.BriarApplication.ENTRY_ACTIVITY;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_RINGTONE;
import static org.briarproject.briar.android.navdrawer.NavDrawerActivity.SIGN_OUT_URI;
import static org.briarproject.briar.android.util.UiUtils.hasScreenLock;
import static org.briarproject.briar.android.util.UiUtils.triggerFeedback;
import static org.briarproject.briar.api.android.AndroidNotificationManager.BLOG_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.CONTACT_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.FORUM_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.GROUP_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_BLOG;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_FORUM;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_GROUP;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_PRIVATE;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_RINGTONE_NAME;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_RINGTONE_URI;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_SOUND;
import static org.briarproject.briar.api.android.AndroidNotificationManager.PREF_NOTIFY_VIBRATION;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SettingsFragment extends PreferenceFragmentCompat
		implements EventListener, OnPreferenceChangeListener {

	public static final String SETTINGS_NAMESPACE = "android-ui";
	public static final String LANGUAGE = "pref_key_language";
	public static final String PREF_SCREEN_LOCK = "pref_key_lock";
	public static final String PREF_SCREEN_LOCK_TIMEOUT =
			"pref_key_lock_timeout";
	public static final String NOTIFY_SIGN_IN = "pref_key_notify_sign_in";

	private static final String TOR_NAMESPACE = TorConstants.ID.getString();
	private static final String TOR_NETWORK = "pref_key_tor_network";
	private static final String TOR_MOBILE = "pref_key_tor_mobile_data";
	private static final String TOR_ONLY_WHEN_CHARGING =
			"pref_key_tor_only_when_charging";

	private static final Logger LOG =
			Logger.getLogger(SettingsFragment.class.getName());

	private SettingsActivity listener;
	private ListPreference language;
	private ListPreference torNetwork;
	private SwitchPreference torMobile;
	private SwitchPreference torOnlyWhenCharging;
	private SwitchPreference screenLock;
	private ListPreference screenLockTimeout;
	private SwitchPreference notifyPrivateMessages;
	private SwitchPreference notifyGroupMessages;
	private SwitchPreference notifyForumPosts;
	private SwitchPreference notifyBlogPosts;
	private SwitchPreference notifyVibration;

	private Preference notifySound;

	// Fields that are accessed from background threads must be volatile
	private volatile Settings settings, torSettings;
	private volatile boolean settingsLoaded = false;

	@Inject
	volatile SettingsManager settingsManager;
	@Inject
	volatile EventBus eventBus;
	@Inject
	LocationUtils locationUtils;
	@Inject
	CircumventionProvider circumventionProvider;

	@Inject
	AndroidExecutor androidExecutor;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (SettingsActivity) context;
		listener.getActivityComponent().inject(this);
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.settings);

		language = findPreference(LANGUAGE);
		setLanguageEntries();
		ListPreference theme = findPreference("pref_key_theme");
		torNetwork = findPreference(TOR_NETWORK);
		torMobile = findPreference(TOR_MOBILE);
		torOnlyWhenCharging = findPreference(TOR_ONLY_WHEN_CHARGING);
		screenLock = findPreference(PREF_SCREEN_LOCK);
		screenLockTimeout = findPreference(PREF_SCREEN_LOCK_TIMEOUT);
		notifyPrivateMessages =
				findPreference("pref_key_notify_private_messages");
		notifyGroupMessages = findPreference("pref_key_notify_group_messages");
		notifyForumPosts = findPreference("pref_key_notify_forum_posts");
		notifyBlogPosts = findPreference("pref_key_notify_blog_posts");
		notifyVibration = findPreference("pref_key_notify_vibration");
		notifySound = findPreference("pref_key_notify_sound");

		language.setOnPreferenceChangeListener(this);
		theme.setOnPreferenceChangeListener((preference, newValue) -> {
			if (getActivity() != null) {
				// activate new theme
				UiUtils.setTheme(getActivity(), (String) newValue);
				// bring up parent activity, so it can change its theme as well
				// upstream bug: https://issuetracker.google.com/issues/38352704
				Intent intent = new Intent(getActivity(), ENTRY_ACTIVITY);
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
		torNetwork.setOnPreferenceChangeListener(this);
		torMobile.setOnPreferenceChangeListener(this);
		torOnlyWhenCharging.setOnPreferenceChangeListener(this);
		screenLock.setOnPreferenceChangeListener(this);
		screenLockTimeout.setOnPreferenceChangeListener(this);

		findPreference("pref_key_send_feedback").setOnPreferenceClickListener(
				preference -> {
					triggerFeedback(androidExecutor);
					return true;
				});

		if (SDK_INT < 27) {
			// remove System Default Theme option from preference entries
			// as it is not functional on this API anyway
			List<CharSequence> entries =
					new ArrayList<>(Arrays.asList(theme.getEntries()));
			entries.remove(getString(R.string.pref_theme_system));
			theme.setEntries(entries.toArray(new CharSequence[0]));
			// also remove corresponding value
			List<CharSequence> values =
					new ArrayList<>(Arrays.asList(theme.getEntryValues()));
			values.remove(getString(R.string.pref_theme_system_value));
			theme.setEntryValues(values.toArray(new CharSequence[0]));
		}
		if (IS_DEBUG_BUILD) {
			findPreference("pref_key_explode").setOnPreferenceClickListener(
					preference -> {
						throw new RuntimeException("Boom!");
					}
			);
		} else {
			findPreference("pref_key_explode").setVisible(false);
			findPreference("pref_key_test_data").setVisible(false);
			PreferenceGroup testing =
					findPreference("pref_key_explode").getParent();
			if (testing == null) throw new AssertionError();
			testing.setVisible(false);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);
		ColorDrawable divider = new ColorDrawable(
				ContextCompat.getColor(requireContext(), R.color.divider));
		setDivider(divider);
		return view;
	}

	@Override
	public void onStart() {
		super.onStart();
		eventBus.addListener(this);
		setSettingsEnabled(false);
		loadSettings();
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

	private void setTorNetworkSummary(int torNetworkSetting) {
		if (torNetworkSetting != PREF_TOR_NETWORK_AUTOMATIC) {
			torNetwork.setSummary("%s");  // use setting value
			return;
		}

		// Look up country name in the user's chosen language if available
		String country = locationUtils.getCurrentCountry();
		String countryName = country;
		for (Locale locale : Locale.getAvailableLocales()) {
			if (locale.getCountry().equalsIgnoreCase(country)) {
				countryName = locale.getDisplayCountry();
				break;
			}
		}
		boolean blocked =
				circumventionProvider.isTorProbablyBlocked(country);
		boolean useBridges = circumventionProvider.doBridgesWork(country);
		String setting =
				getString(R.string.tor_network_setting_without_bridges);
		if (blocked && useBridges) {
			setting = getString(R.string.tor_network_setting_with_bridges);
		} else if (blocked) {
			setting = getString(R.string.tor_network_setting_never);
		}
		torNetwork.setSummary(
				getString(R.string.tor_network_setting_summary, setting,
						countryName));
	}

	private void loadSettings() {
		listener.runOnDbThread(() -> {
			try {
				long start = now();
				settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
				torSettings = migrateTorSettings(
						settingsManager.getSettings(TOR_NAMESPACE));
				settingsLoaded = true;
				logDuration(LOG, "Loading settings", start);
				displaySettings();
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	// TODO: Remove after a reasonable migration period (added 2020-01-29)
	private Settings migrateTorSettings(Settings s) {
		int network = s.getInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_AUTOMATIC);
		if (network == PREF_TOR_NETWORK_NEVER) {
			s.putInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_AUTOMATIC);
			s.putBoolean(PREF_PLUGIN_ENABLE, false);
			// We don't need to save the migrated settings - the Tor plugin is
			// responsible for that. This code just handles the case where the
			// settings are loaded before the plugin migrates them.
		}
		return s;
	}

	private void displaySettings() {
		listener.runOnUiThreadUnlessDestroyed(() -> {
			// due to events, we might try to display before a load completed
			if (!settingsLoaded) return;

			int torNetworkSetting = torSettings.getInt(PREF_TOR_NETWORK,
					PREF_TOR_NETWORK_AUTOMATIC);
			torNetwork.setValue(Integer.toString(torNetworkSetting));
			setTorNetworkSummary(torNetworkSetting);

			boolean torMobileSetting =
					torSettings.getBoolean(PREF_TOR_MOBILE, true);
			torMobile.setChecked(torMobileSetting);

			boolean torChargingSetting =
					torSettings.getBoolean(PREF_TOR_ONLY_WHEN_CHARGING, false);
			torOnlyWhenCharging.setChecked(torChargingSetting);

			displayScreenLockSetting();

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
				notifySound.setVisible(false);
			}
			setSettingsEnabled(true);
		});
	}

	private void setSettingsEnabled(boolean enabled) {
		// preferences not needed here, because handled by SharedPreferences:
		// - pref_key_theme
		// - pref_key_notify_sign_in
		// preferences partly needed here, because they have their own logic
		// - pref_key_lock (screenLock -> displayScreenLockSetting())
		// - pref_key_lock_timeout (screenLockTimeout)
		torNetwork.setEnabled(enabled);
		torMobile.setEnabled(enabled);
		torOnlyWhenCharging.setEnabled(enabled);
		if (!enabled) screenLock.setEnabled(false);
		notifyPrivateMessages.setEnabled(enabled);
		notifyGroupMessages.setEnabled(enabled);
		notifyForumPosts.setEnabled(enabled);
		notifyBlogPosts.setEnabled(enabled);
		notifyVibration.setEnabled(enabled);
		notifySound.setEnabled(enabled);
	}

	private void displayScreenLockSetting() {
		if (SDK_INT < 21) {
			screenLock.setVisible(false);
			screenLockTimeout.setVisible(false);
		} else {
			if (getActivity() != null && hasScreenLock(getActivity())) {
				screenLock.setEnabled(true);
				screenLock.setChecked(
						settings.getBoolean(PREF_SCREEN_LOCK, false));
				screenLock.setSummary(R.string.pref_lock_summary);
			} else {
				screenLock.setEnabled(false);
				screenLock.setChecked(false);
				screenLock.setSummary(R.string.pref_lock_disabled_summary);
			}
			// timeout depends on screenLock and gets disabled automatically
			int timeout = settings.getInt(PREF_SCREEN_LOCK_TIMEOUT,
					Integer.valueOf(getString(
							R.string.pref_lock_timeout_value_default)));
			String newValue = String.valueOf(timeout);
			screenLockTimeout.setValue(newValue);
			setScreenLockTimeoutSummary(newValue);
		}
	}

	private void setScreenLockTimeoutSummary(String timeout) {
		String never = getString(R.string.pref_lock_timeout_value_never);
		if (timeout.equals(never)) {
			screenLockTimeout
					.setSummary(R.string.pref_lock_timeout_never_summary);
		} else {
			screenLockTimeout
					.setSummary(R.string.pref_lock_timeout_summary);
		}
	}

	@TargetApi(26)
	private void setupNotificationPreference(SwitchPreference pref,
			String channelId, @StringRes int summary) {
		pref.setWidgetLayoutResource(0);
		pref.setSummary(summary);
		pref.setOnPreferenceClickListener(clickedPref -> {
			String packageName = requireContext().getPackageName();
			Intent intent = new Intent(ACTION_CHANNEL_NOTIFICATION_SETTINGS)
					.putExtra(EXTRA_APP_PACKAGE, packageName)
					.putExtra(EXTRA_CHANNEL_ID, channelId);
			Context ctx = requireContext();
			if (intent.resolveActivity(ctx.getPackageManager()) != null) {
				startActivity(intent);
			} else {
				Toast.makeText(ctx, R.string.error_start_activity, LENGTH_SHORT)
						.show();
			}
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
		if (i.resolveActivity(requireActivity().getPackageManager()) != null) {
			startActivityForResult(i, REQUEST_RINGTONE);
		} else {
			Toast.makeText(getContext(), R.string.cannot_load_ringtone,
					LENGTH_SHORT).show();
		}
		return true;
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference == language) {
			if (!language.getValue().equals(newValue))
				languageChanged((String) newValue);
			return false;
		} else if (preference == torNetwork) {
			int torNetworkSetting = Integer.valueOf((String) newValue);
			storeTorNetworkSetting(torNetworkSetting);
			setTorNetworkSummary(torNetworkSetting);
		} else if (preference == torMobile) {
			boolean torMobileSetting = (Boolean) newValue;
			storeTorMobileSetting(torMobileSetting);
		} else if (preference == torOnlyWhenCharging) {
			boolean torChargingSetting = (Boolean) newValue;
			storeTorChargingSetting(torChargingSetting);
		} else if (preference == screenLock) {
			Settings s = new Settings();
			s.putBoolean(PREF_SCREEN_LOCK, (Boolean) newValue);
			storeSettings(s);
		} else if (preference == screenLockTimeout) {
			Settings s = new Settings();
			String value = (String) newValue;
			s.putInt(PREF_SCREEN_LOCK_TIMEOUT, Integer.valueOf(value));
			storeSettings(s);
			setScreenLockTimeoutSummary(value);
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
					Intent intent = new Intent(getContext(), ENTRY_ACTIVITY);
					intent.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
					intent.setData(SIGN_OUT_URI);
					requireActivity().startActivity(intent);
					requireActivity().finish();
				});
		builder.setNegativeButton(R.string.cancel, null);
		builder.setCancelable(false);
		builder.show();
	}

	private void storeTorNetworkSetting(int torNetworkSetting) {
		Settings s = new Settings();
		s.putInt(PREF_TOR_NETWORK, torNetworkSetting);
		mergeSettings(s, TOR_NAMESPACE);
	}

	private void storeTorMobileSetting(boolean torMobileSetting) {
		Settings s = new Settings();
		s.putBoolean(PREF_TOR_MOBILE, torMobileSetting);
		mergeSettings(s, TOR_NAMESPACE);
	}

	private void storeTorChargingSetting(boolean torChargingSetting) {
		Settings s = new Settings();
		s.putBoolean(PREF_TOR_ONLY_WHEN_CHARGING, torChargingSetting);
		mergeSettings(s, TOR_NAMESPACE);
	}

	private void storeSettings(Settings s) {
		mergeSettings(s, SETTINGS_NAMESPACE);
	}

	private void mergeSettings(Settings s, String namespace) {
		listener.runOnDbThread(() -> {
			try {
				long start = now();
				settingsManager.mergeSettings(s, namespace);
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
				if (r == null || "file".equals(uri.getScheme())) {
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
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			String namespace = s.getNamespace();
			if (namespace.equals(SETTINGS_NAMESPACE)) {
				LOG.info("Settings updated");
				settings = s.getSettings();
				displaySettings();
			} else if (namespace.equals(TOR_NAMESPACE)) {
				LOG.info("Tor settings updated");
				torSettings = migrateTorSettings(s.getSettings());
				displaySettings();
			}
		}
	}

}
