package org.briarproject.android;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import org.briarproject.R;
import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.settings.SettingsManager;

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
	public void injectActivity(AndroidComponent component) {
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

	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return false;
	}

}
