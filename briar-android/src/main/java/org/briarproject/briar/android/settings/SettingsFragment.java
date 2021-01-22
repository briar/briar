package org.briarproject.briar.android.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import static android.app.Activity.RESULT_OK;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_AVATAR_IMAGE;
import static org.briarproject.briar.android.util.UiUtils.createSelectImageIntent;
import static org.briarproject.briar.android.util.UiUtils.triggerFeedback;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SettingsFragment extends PreferenceFragmentCompat {

	public static final String SETTINGS_NAMESPACE = "android-ui";

	private static final String PREF_KEY_AVATAR = "pref_key_avatar";
	private static final String PREF_KEY_FEEDBACK = "pref_key_send_feedback";
	private static final String PREF_KEY_DEV = "pref_key_dev";
	private static final String PREF_KEY_EXPLODE = "pref_key_explode";

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SettingsViewModel viewModel;
	private AvatarPreference prefAvatar;

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
				Intent intent = createSelectImageIntent(false);
				startActivityForResult(intent, REQUEST_AVATAR_IMAGE);
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

		Preference explode = requireNonNull(findPreference(PREF_KEY_EXPLODE));
		if (IS_DEBUG_BUILD) {
			explode.setOnPreferenceClickListener(preference -> {
				throw new RuntimeException("Boom!");
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

	@Override
	public void onActivityResult(int request, int result,
			@Nullable Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_AVATAR_IMAGE && result == RESULT_OK) {
			if (data == null) return;
			Uri uri = data.getData();
			if (uri == null) return;

			DialogFragment dialog =
					ConfirmAvatarDialogFragment.newInstance(uri);
			dialog.show(getParentFragmentManager(),
					ConfirmAvatarDialogFragment.TAG);
		}
	}

}
