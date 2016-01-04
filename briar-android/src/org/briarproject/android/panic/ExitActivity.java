package org.briarproject.android.panic;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.briarproject.android.BaseActivity;

public class ExitActivity extends BaseActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (Build.VERSION.SDK_INT >= 21) {
			finishAndRemoveTask();
		} else {
			finish();
		}

		System.exit(0);
	}

	public static void exitAndRemoveFromRecentApps(final BaseActivity activity) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Intent intent = new Intent(activity, ExitActivity.class);

				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
						| Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
						| Intent.FLAG_ACTIVITY_CLEAR_TASK
						| Intent.FLAG_ACTIVITY_NO_ANIMATION);

				activity.startActivity(intent);
			}
		});

	}
}