package org.briarproject.briar.android.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.transition.Slide;
import android.transition.Transition;
import android.view.Gravity;
import android.view.Window;

import org.briarproject.briar.R;
import org.briarproject.briar.android.controller.BriarController;
import org.briarproject.briar.android.controller.DbController;
import org.briarproject.briar.android.controller.handler.ResultHandler;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.login.PasswordActivity;
import org.briarproject.briar.android.panic.ExitActivity;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PASSWORD;

@SuppressLint("Registered")
public abstract class BriarActivity extends BaseActivity {

	public static final String KEY_STARTUP_FAILED = "briar.STARTUP_FAILED";
	public static final String GROUP_ID = "briar.GROUP_ID";
	public static final String GROUP_NAME = "briar.GROUP_NAME";

	private static final Logger LOG =
			Logger.getLogger(BriarActivity.class.getName());

	@Inject
	BriarController briarController;

	@Deprecated
	@Inject
	DbController dbController;

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_PASSWORD) {
			if (result == RESULT_OK) briarController.startAndBindService();
			else supportFinishAfterTransition();
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		if (!briarController.hasEncryptionKey() && !isFinishing()) {
			Intent i = new Intent(this, PasswordActivity.class);
			startActivityForResult(i, REQUEST_PASSWORD);
		}
	}

	protected void showInitialFragment(BaseFragment f) {
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, f, f.getUniqueTag())
				.commit();
	}

	public void showNextFragment(BaseFragment f) {
		getSupportFragmentManager().beginTransaction()
				.setCustomAnimations(R.anim.step_next_in,
						R.anim.step_previous_out, R.anim.step_previous_in,
						R.anim.step_next_out)
				.replace(R.id.fragmentContainer, f, f.getUniqueTag())
				.addToBackStack(f.getUniqueTag())
				.commit();
	}

	public void setSceneTransitionAnimation() {
		if (Build.VERSION.SDK_INT < 21) return;
		Transition slide = new Slide(Gravity.RIGHT);
		slide.excludeTarget(android.R.id.statusBarBackground, true);
		slide.excludeTarget(android.R.id.navigationBarBackground, true);
		Window window = getWindow();
		window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
		window.setExitTransition(slide);
		window.setEnterTransition(slide);
		window.setTransitionBackgroundFadeDuration(getResources()
				.getInteger(android.R.integer.config_longAnimTime));
		window.setBackgroundDrawableResource(android.R.color.transparent);
	}

	/**
	 * This should be called after the content view has been added in onCreate()
	 *
	 * @param ownLayout true if the custom toolbar brings its own layout
	 * @return the Toolbar object or null if content view did not contain one
	 */
	@Nullable
	protected Toolbar setUpCustomToolbar(boolean ownLayout) {
		// Custom Toolbar
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayShowHomeEnabled(true);
			ab.setDisplayHomeAsUpEnabled(true);
			ab.setDisplayShowCustomEnabled(ownLayout);
			ab.setDisplayShowTitleEnabled(!ownLayout);
		}
		return toolbar;
	}

	protected void signOut(final boolean removeFromRecentApps) {
		if (briarController.hasEncryptionKey()) {
			// Don't use UiResultHandler because we want the result even if
			// this activity has been destroyed
			briarController.signOut(new ResultHandler<Void>() {
				@Override
				public void onResult(Void result) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							exit(removeFromRecentApps);
						}
					});
				}
			});
		} else {
			exit(removeFromRecentApps);
		}
	}

	private void exit(boolean removeFromRecentApps) {
		if (removeFromRecentApps) startExitActivity();
		else finishAndExit();
	}

	private void startExitActivity() {
		Intent i = new Intent(this, ExitActivity.class);
		i.addFlags(FLAG_ACTIVITY_NEW_TASK
				| FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
				| FLAG_ACTIVITY_NO_ANIMATION
				| FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(i);
	}

	private void finishAndExit() {
		if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask();
		else supportFinishAfterTransition();
		LOG.info("Exiting");
		System.exit(0);
	}

	@Deprecated
	public void runOnDbThread(Runnable task) {
		dbController.runOnDbThread(task);
	}

	@Deprecated
	protected void finishOnUiThread() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				supportFinishAfterTransition();
			}
		});
	}
}
