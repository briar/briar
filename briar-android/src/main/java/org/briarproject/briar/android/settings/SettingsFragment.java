package org.briarproject.briar.android.settings;

import android.os.Bundle;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;
import static org.briarproject.briar.android.util.UiUtils.triggerFeedback;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SettingsFragment extends PreferenceFragmentCompat {

	public static final String SETTINGS_NAMESPACE = "android-ui";

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.settings);

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
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.settings_button);
	}

}
