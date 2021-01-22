package org.briarproject.briar.android.settings;

import android.annotation.TargetApi;
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
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;

import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;

import static android.app.Activity.RESULT_OK;
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
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_RINGTONE;
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
	public static final String NOTIFY_SIGN_IN = "pref_key_notify_sign_in";

	private static final Logger LOG =
			Logger.getLogger(SettingsFragment.class.getName());

	private SettingsActivity listener;
	private SwitchPreference notifyPrivateMessages;
	private SwitchPreference notifyGroupMessages;
	private SwitchPreference notifyForumPosts;
	private SwitchPreference notifyBlogPosts;
	private SwitchPreference notifyVibration;

	private Preference notifySound;

	// Fields that are accessed from background threads must be volatile
	private volatile Settings settings;
	private volatile boolean settingsLoaded = false;

	@Inject
	volatile SettingsManager settingsManager;
	@Inject
	volatile EventBus eventBus;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (SettingsActivity) context;
		listener.getActivityComponent().inject(this);
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.settings);

		notifyPrivateMessages =
				findPreference("pref_key_notify_private_messages");
		notifyGroupMessages = findPreference("pref_key_notify_group_messages");
		notifyForumPosts = findPreference("pref_key_notify_forum_posts");
		notifyBlogPosts = findPreference("pref_key_notify_blog_posts");
		notifyVibration = findPreference("pref_key_notify_vibration");
		notifySound = findPreference("pref_key_notify_sound");

		Preference prefFeedback =
				requireNonNull(findPreference("pref_key_send_feedback"));
		prefFeedback.setOnPreferenceClickListener(preference -> {
			triggerFeedback(requireContext());
			return true;
		});

		Preference explode = requireNonNull(findPreference("pref_key_explode"));
		if (IS_DEBUG_BUILD) {
			explode.setOnPreferenceClickListener(preference -> {
				throw new RuntimeException("Boom!");
			});
		} else {
			explode.setVisible(false);
			findPreference("pref_key_test_data").setVisible(false);
			PreferenceGroup testing = explode.getParent();
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
		requireActivity().setTitle(R.string.settings_button);
		eventBus.addListener(this);
		setSettingsEnabled(false);
		loadSettings();
	}

	@Override
	public void onStop() {
		super.onStop();
		eventBus.removeListener(this);
	}

	private void loadSettings() {
		listener.runOnDbThread(() -> {
			try {
				long start = now();
				settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
				settingsLoaded = true;
				logDuration(LOG, "Loading settings", start);
				displaySettings();
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void displaySettings() {
		listener.runOnUiThreadUnlessDestroyed(() -> {
			// due to events, we might try to display before a load completed
			if (!settingsLoaded) return;

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
		// - pref_key_notify_sign_in
		notifyPrivateMessages.setEnabled(enabled);
		notifyGroupMessages.setEnabled(enabled);
		notifyForumPosts.setEnabled(enabled);
		notifyBlogPosts.setEnabled(enabled);
		notifyVibration.setEnabled(enabled);
		notifySound.setEnabled(enabled);
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
		if (preference == notifyPrivateMessages) {
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
	public void onActivityResult(int request, int result,
			@Nullable Intent data) {
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
			}
		}
	}

}
