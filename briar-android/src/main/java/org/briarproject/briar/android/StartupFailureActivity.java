package org.briarproject.briar.android;

import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;

import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult;
import static org.briarproject.briar.android.BriarService.EXTRA_NOTIFICATION_ID;
import static org.briarproject.briar.android.BriarService.EXTRA_START_RESULT;

public class StartupFailureActivity extends BaseActivity {

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_startup_failure);
		handleIntent(getIntent());
	}

	@Override
	public void injectActivity(ActivityComponent component) {

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
		TextView view = findViewById(R.id.errorView);
		if (result.equals(StartResult.DB_ERROR)) {
			view.setText(getText(R.string.startup_failed_db_error));
		} else if (result.equals(StartResult.SERVICE_ERROR)) {
			view.setText(getText(R.string.startup_failed_service_error));
		}
	}

}
