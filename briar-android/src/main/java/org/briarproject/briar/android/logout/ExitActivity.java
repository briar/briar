package org.briarproject.briar.android.logout;

import android.app.Activity;
import android.os.Bundle;

import java.util.logging.Logger;

import static android.os.Build.VERSION.SDK_INT;

public class ExitActivity extends Activity {

	private static final Logger LOG =
			Logger.getLogger(ExitActivity.class.getName());

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		if (SDK_INT >= 21) finishAndRemoveTask();
		else finish();
		LOG.info("Exiting");
		System.exit(0);
	}
}