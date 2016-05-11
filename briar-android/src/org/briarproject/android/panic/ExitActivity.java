package org.briarproject.android.panic;

import android.os.Build;
import android.os.Bundle;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.AndroidComponent;
import org.briarproject.android.BaseActivity;

import java.util.logging.Logger;

public class ExitActivity extends BaseActivity {

	private static final Logger LOG =
			Logger.getLogger(ExitActivity.class.getName());

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask();
		else finish();
		LOG.info("Exiting");
		System.exit(0);
	}

	@Override
	public void injectActivity(ActivityComponent component) {

	}
}