package org.briarproject.briar.android.logout;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

import java.util.logging.Logger;

import static android.os.Build.VERSION.SDK_INT;
import static java.util.logging.Logger.getLogger;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ExitActivity extends Activity {

	private static final Logger LOG = getLogger(ExitActivity.class.getName());

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		if (SDK_INT >= 21) finishAndRemoveTask();
		else finish();
		LOG.info("Exiting");
		System.exit(0);
	}
}