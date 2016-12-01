package org.briarproject.briar.android.settings;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.inject.Inject;

public class SettingsActivity extends BriarActivity {

	@Inject
	protected AndroidExecutor androidExecutor;
	@Inject
	protected SettingsManager settingsManager;
	@Inject
	protected EventBus eventBus;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

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

	public AndroidExecutor getAndroidExecutor() {
		return androidExecutor;
	}

	public SettingsManager getSettingsManager() {
		return settingsManager;
	}

	public EventBus getEventBus() {
		return eventBus;
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
