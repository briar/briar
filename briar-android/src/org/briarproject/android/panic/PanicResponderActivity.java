package org.briarproject.android.panic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;

import org.briarproject.android.BriarActivity;

import java.util.logging.Logger;

public class PanicResponderActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(PanicResponderActivity.class.getName());

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(this);

		Intent intent = getIntent();
		if (intent != null && sharedPref.getBoolean("pref_key_lock", true)) {
			LOG.info("Signing out...");
			signOut(true);
		}

		if (Build.VERSION.SDK_INT >= 21) {
			finishAndRemoveTask();
		} else {
			finish();
		}
	}
}