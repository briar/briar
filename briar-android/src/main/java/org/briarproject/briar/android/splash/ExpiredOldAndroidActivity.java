package org.briarproject.briar.android.splash;

import android.content.Intent;
import android.os.Bundle;

import org.briarproject.bramble.api.system.AndroidWakeLockManager;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.BriarController;
import org.briarproject.briar.android.logout.ExitActivity;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ExpiredOldAndroidActivity extends BaseActivity {

	@Inject
	BriarController briarController;
	@Inject
	AndroidWakeLockManager wakeLockManager;

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_expired_old_android);
		findViewById(R.id.delete_account_button).setOnClickListener(v -> {
			// Hold a wake lock to ensure we exit before the device goes to sleep
			wakeLockManager.runWakefully(() -> {
				// we're not signed in, just go ahead and delete
				briarController.deleteAccount();
				// remove from recent apps
				Intent i = new Intent(this, ExitActivity.class);
				i.addFlags(FLAG_ACTIVITY_NEW_TASK
						| FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
						| FLAG_ACTIVITY_NO_ANIMATION
						| FLAG_ACTIVITY_CLEAR_TASK);
				startActivity(i);
			}, "DeleteAccount");
		});
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}
}
