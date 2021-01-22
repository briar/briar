package org.briarproject.briar.android.settings;

import android.os.Bundle;
import android.view.MenuItem;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SettingsActivity extends BriarActivity
		implements OnPreferenceStartFragmentCallback {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle bundle) {
		super.onCreate(bundle);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_settings);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return false;
	}

	@Override
	public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller,
			Preference pref) {
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentFactory fragmentFactory = fragmentManager.getFragmentFactory();
		Fragment fragment = fragmentFactory
				.instantiate(getClassLoader(), pref.getFragment());
		fragment.setTargetFragment(caller, 0);
		// Replace the existing Fragment with the new Fragment
		fragmentManager.beginTransaction()
				.setCustomAnimations(R.anim.step_next_in,
						R.anim.step_previous_out, R.anim.step_previous_in,
						R.anim.step_next_out)
				.replace(R.id.fragmentContainer, fragment)
				.addToBackStack(null)
				.commit();
		return true;
	}

	/**
	 * If the preference is not yet enabled, this enables the preference
	 * and makes it persist changed values.
	 * Call this after setting the initial value
	 * to prevent this change from getting persisted in the DB unnecessarily.
	 */
	static void enableAndPersist(Preference pref) {
		if (!pref.isEnabled()) {
			pref.setEnabled(true);
			pref.setPersistent(true);
		}
	}

}
