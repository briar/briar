package org.briarproject.briar.android;

import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.fragment.ErrorFragment;

import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult;
import static org.briarproject.briar.android.BriarService.EXTRA_NOTIFICATION_ID;
import static org.briarproject.briar.android.BriarService.EXTRA_START_RESULT;

public class StartupFailureActivity extends BaseActivity implements
		BaseFragmentListener {

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_fragment_container);
		handleIntent(getIntent());
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void handleIntent(Intent i) {
		StartResult result =
				(StartResult) i.getSerializableExtra(EXTRA_START_RESULT);
		int notificationId = i.getIntExtra(EXTRA_NOTIFICATION_ID, -1);

		// cancel notification
		if (notificationId > -1) {
			Object o = getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.cancel(notificationId);
		}

		// show proper error message
		String errorMsg;
		switch (result) {
			case DATA_TOO_OLD_ERROR:
				errorMsg = getString(R.string.startup_failed_db_error);
				break;
			case DATA_TOO_NEW_ERROR:
				errorMsg =
						getString(R.string.startup_failed_data_too_new_error);
				break;
			case DB_ERROR:
				errorMsg =
						getString(R.string.startup_failed_data_too_old_error);
				break;
			case SERVICE_ERROR:
				errorMsg = getString(R.string.startup_failed_service_error);
				break;
			default:
				throw new IllegalArgumentException();
		}
		showInitialFragment(ErrorFragment.newInstance(errorMsg));
	}

	@Override
	public void runOnDbThread(Runnable runnable) {
		throw new AssertionError("Deprecated and should not be used");
	}

}
