package org.briarproject.android;

import android.app.Activity;
import android.os.Bundle;

import org.briarproject.R;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;

public class ExpiredActivity extends Activity {

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		setContentView(R.layout.activity_expired);
	}
}
