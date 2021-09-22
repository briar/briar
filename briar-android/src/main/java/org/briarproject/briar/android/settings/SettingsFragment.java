package org.briarproject.briar.android.settings;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.ActivityLaunchers.CreateDocumentAdvanced;
import org.briarproject.briar.android.util.ActivityLaunchers.GetImageAdvanced;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;
import static org.briarproject.briar.android.util.UiUtils.triggerFeedback;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SettingsFragment extends PreferenceFragmentCompat {

	public static final String SETTINGS_NAMESPACE = "android-ui";

	private static final String PREF_KEY_AVATAR = "pref_key_avatar";
	private static final String PREF_KEY_FEEDBACK = "pref_key_send_feedback";
	private static final String PREF_KEY_DEV = "pref_key_dev";
	private static final String PREF_KEY_EXPLODE = "pref_key_explode";
	private static final String PREF_KEY_SHARE_APP = "pref_key_share_app";
	private static final String PREF_KEY_EXPORT_LOG = "pref_key_export_log";
	private static final String PREF_EXPORT_OLD_LOG = "pref_key_export_old_log";

	private static final String LOG_EXPORT_FILENAME = "briar-log.txt";

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SettingsViewModel viewModel;
	private AvatarPreference prefAvatar;

	private final ActivityResultLauncher<String> imageLauncher =
			registerForActivityResult(new GetImageAdvanced(),
					this::onImageSelected);

	private final ActivityResultLauncher<String> logLauncher =
			registerForActivityResult(new CreateDocumentAdvanced(),
					uri -> onLogFileSelected(false, uri));

	private final ActivityResultLauncher<String> oldLogLauncher =
			registerForActivityResult(new CreateDocumentAdvanced(),
					uri -> onLogFileSelected(true, uri));

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		getAndroidComponent(context).inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(SettingsViewModel.class);
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.settings);

		prefAvatar = requireNonNull(findPreference(PREF_KEY_AVATAR));
		if (viewModel.shouldEnableProfilePictures()) {
			prefAvatar.setOnPreferenceClickListener(preference -> {
				imageLauncher.launch("image/*");
				return true;
			});
		} else {
			prefAvatar.setVisible(false);
		}

		Preference prefFeedback =
				requireNonNull(findPreference(PREF_KEY_FEEDBACK));
		prefFeedback.setOnPreferenceClickListener(preference -> {
			triggerFeedback(requireContext());
			return true;
		});

		if (IS_DEBUG_BUILD) {
			Preference explode =
					requireNonNull(findPreference(PREF_KEY_EXPLODE));
			explode.setOnPreferenceClickListener(preference -> {
				throw new RuntimeException("Boom!");
			});
			Preference exportLog =
					requireNonNull(findPreference(PREF_KEY_EXPORT_LOG));
			exportLog.setOnPreferenceClickListener(preference -> {
				logLauncher.launch(LOG_EXPORT_FILENAME);
				return true;
			});
			Preference exportOldLog =
					requireNonNull(findPreference(PREF_EXPORT_OLD_LOG));
			exportOldLog.setOnPreferenceClickListener(preference -> {
				oldLogLauncher.launch(LOG_EXPORT_FILENAME);
				return true;
			});
		} else {
			PreferenceGroup dev = requireNonNull(findPreference(PREF_KEY_DEV));
			dev.setVisible(false);
		}
	}

	@Override
	public void onViewCreated(@NonNull View view,
			@Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		viewModel.getOwnIdentityInfo().observe(getViewLifecycleOwner(), us ->
				prefAvatar.setOwnIdentityInfo(us)
		);
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.settings_button);
	}

	private void onImageSelected(@Nullable Uri uri) {
		if (uri == null) return;
		DialogFragment dialog = ConfirmAvatarDialogFragment.newInstance(uri);
		dialog.show(getParentFragmentManager(),
				ConfirmAvatarDialogFragment.TAG);
	}

	private void onLogFileSelected(boolean old, @Nullable Uri uri) {
		if (uri != null) viewModel.exportPersistentLog(old, uri);
	}
}
