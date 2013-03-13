package net.sf.briar.android;

import roboguice.activity.RoboActivity;
import android.os.Bundle;

/**
 * An abstract superclass for activities that overrides the default behaviour
 * to prevent sensitive state from being saved unless the subclass explicitly
 * saves it.
 */
public abstract class BriarActivity extends RoboActivity {

	@Override
	public void onCreate(Bundle state) {
		// Don't pass state through to the superclass
		super.onCreate(null);
	}

	@Override
	public void onRestoreInstanceState(Bundle state) {
		// Don't pass state through to the superclass
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		// Don't allow the superclass to save state
	}

	protected void finishOnUiThread() {
		runOnUiThread(new Runnable() {
			public void run() {
				finish();
			}
		});
	}
}
