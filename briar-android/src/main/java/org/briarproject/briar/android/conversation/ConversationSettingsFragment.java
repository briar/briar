package org.briarproject.briar.android.conversation;

import android.os.Bundle;

import org.briarproject.briar.R;

import androidx.preference.PreferenceFragmentCompat;

public class ConversationSettingsFragment extends PreferenceFragmentCompat {

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		addPreferencesFromResource(R.xml.conversation_settings);
	}

}
