package org.briarproject.briar.android.settings;

import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import static org.briarproject.briar.android.settings.SettingsFragment.NOTIFY_SIGN_IN;
import static org.briarproject.briar.api.android.AndroidNotificationManager.REMINDER_NOTIFICATION_ID;

public class SettingsActivity extends BriarActivity {

	public static final String NO_NOTIFY_SIGN_IN = "noNotifySignIn";

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		// Maybe turn off sign-in reminder
		Intent intent = getIntent();
		if (intent != null && NO_NOTIFY_SIGN_IN.equals(intent.getAction())) {
			// Turn it off
			BriarApplication app = (BriarApplication) getApplication();
			SharedPreferences prefs = app.getDefaultSharedPreferences();
			prefs.edit().putBoolean(NOTIFY_SIGN_IN, false).apply();
			// Remove sign-in reminder notification
			NotificationManager nm = (NotificationManager)
					getSystemService(NOTIFICATION_SERVICE);
			if (nm != null) nm.cancel(REMINDER_NOTIFICATION_ID);
			// Finish this activity again
			finish();
		}

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_settings);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return false;
	}
}
