package org.briarproject.android.panic;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;

import org.briarproject.R;

public class PanicPreferencesFragment extends PreferenceFragmentCompat {

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.panic_preferences);
	}
}
