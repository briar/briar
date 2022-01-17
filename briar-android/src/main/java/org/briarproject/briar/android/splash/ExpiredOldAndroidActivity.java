package org.briarproject.briar.android.splash;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;
import org.briarproject.bramble.api.system.Wakeful;
import org.briarproject.briar.R;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.BriarController;
import org.briarproject.briar.android.logout.ExitActivity;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static org.briarproject.briar.android.TestingConstants.PREVENT_SCREENSHOTS;

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

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		setContentView(R.layout.activity_expired_old_android);
		findViewById(R.id.delete_account_button).setOnClickListener(v ->
				signOutAndDeleteAccount());
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(
				Localizer.getInstance().setLocale(base));
		Localizer.getInstance().setLocale(this);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void signOutAndDeleteAccount() {
		// Hold a wake lock to ensure we exit before the device goes to sleep
		wakeLockManager.runWakefully(() -> {
			if (briarController.accountSignedIn()) {
				// Don't use UiResultHandler because we want the result even if
				// this activity has been destroyed
				briarController.signOut(result -> {
					Runnable exit = this::startExitActivity;
					wakeLockManager.executeWakefully(exit,
							this::runOnUiThread, "SignOut");
				}, true);
			} else {
				briarController.deleteAccount();
				startExitActivity();
			}
		}, "SignOut");
	}

	@Wakeful
	private void startExitActivity() {
		Intent i = new Intent(this, ExitActivity.class);
		i.addFlags(FLAG_ACTIVITY_NEW_TASK
				| FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
				| FLAG_ACTIVITY_NO_ANIMATION
				| FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(i);
	}
}
