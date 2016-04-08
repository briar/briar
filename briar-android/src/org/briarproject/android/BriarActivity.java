package org.briarproject.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import org.briarproject.android.BriarService.BriarBinder;
import org.briarproject.android.BriarService.BriarServiceConnection;
import org.briarproject.android.controller.BriarController;
import org.briarproject.android.controller.ResultHandler;
import org.briarproject.android.panic.ExitActivity;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;

@SuppressLint("Registered")
public abstract class BriarActivity extends BaseActivity {

	public static final String KEY_LOCAL_AUTHOR_HANDLE =
			"briar.LOCAL_AUTHOR_HANDLE";
	public static final String KEY_STARTUP_FAILED = "briar.STARTUP_FAILED";
	public static final String GROUP_ID = "briar.GROUP_ID";

	public static final int REQUEST_PASSWORD = 1;

	private static final Logger LOG =
			Logger.getLogger(BriarActivity.class.getName());

	@Inject
	protected BriarController briarController;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		briarController.startAndBindService();
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_PASSWORD) {
			if (result == RESULT_OK) briarController.startAndBindService();
			else finish();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!briarController.encryptionKey() && !isFinishing()) {
			Intent i = new Intent(this, PasswordActivity.class);
			i.setFlags(FLAG_ACTIVITY_NO_ANIMATION | FLAG_ACTIVITY_SINGLE_TOP);
			startActivityForResult(i, REQUEST_PASSWORD);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		briarController.unbindService();
	}

	protected void signOut(final boolean removeFromRecentApps) {
		briarController.signOut(new ResultHandler<Void, RuntimeException>() {
			@Override
			public void onResult(Void result) {
				if (removeFromRecentApps) startExitActivity();
				else finishAndExit();
			}

			@Override
			public void onException(RuntimeException exception) {
				// TODO ?
			}
		});
	}

	protected void signOut() {
		signOut(false);
	}

	private void startExitActivity() {
		Intent intent = new Intent(BriarActivity.this,
				ExitActivity.class);
		intent.addFlags(FLAG_ACTIVITY_NEW_TASK
				| FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
				| FLAG_ACTIVITY_NO_ANIMATION);
		if (Build.VERSION.SDK_INT >= 11)
			intent.addFlags(FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
	}

	private void finishAndExit() {
		if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask();
		else finish();
		LOG.info("Exiting");
		System.exit(0);
	}

	public void runOnDbThread(final Runnable task) {
		briarController.runOnDbThread(task);
	}

	protected void finishOnUiThread() {
		runOnUiThread(new Runnable() {
			public void run() {
				finish();
			}
		});
	}
}
