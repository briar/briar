package org.briarproject.briar.android.logout;

import android.os.Bundle;

import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;

public class HideUiActivity extends BaseActivity {

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		finish();
	}

	@Override
	public void injectActivity(ActivityComponent component) {

	}
}
