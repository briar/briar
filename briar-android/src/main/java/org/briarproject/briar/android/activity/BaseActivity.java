package org.briarproject.briar.android.activity;

import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.UiThread;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.android.AndroidComponent;
import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.android.DestroyableContext;
import org.briarproject.briar.android.controller.ActivityLifecycleController;
import org.briarproject.briar.android.forum.ForumModule;
import org.briarproject.briar.android.fragment.SFDialogFragment;
import org.briarproject.briar.api.android.ScreenFilterMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static org.briarproject.briar.android.TestingConstants.PREVENT_SCREENSHOTS;

public abstract class BaseActivity extends AppCompatActivity
		implements DestroyableContext {
	protected ActivityComponent activityComponent;

	private final List<ActivityLifecycleController> lifecycleControllers =
			new ArrayList<>();
	private boolean destroyed = false;

	@Inject
	protected ScreenFilterMonitor screenFilterMonitor;
	private SFDialogFragment dialogFrag;

	public abstract void injectActivity(ActivityComponent component);

	public void addLifecycleController(ActivityLifecycleController alc) {
		lifecycleControllers.add(alc);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		AndroidComponent applicationComponent =
				((BriarApplication) getApplication()).getApplicationComponent();

		activityComponent = DaggerActivityComponent.builder()
				.androidComponent(applicationComponent)
				.activityModule(getActivityModule())
				.forumModule(getForumModule())
				.build();

		injectActivity(activityComponent);

		for (ActivityLifecycleController alc : lifecycleControllers) {
			alc.onActivityCreate(this);
		}

	}

	public ActivityComponent getActivityComponent() {
		return activityComponent;
	}

	// This exists to make test overrides easier
	protected ActivityModule getActivityModule() {
		return new ActivityModule(this);
	}

	protected ForumModule getForumModule() {
		return new ForumModule();
	}

	@Override
	protected void onStart() {
		super.onStart();
		for (ActivityLifecycleController alc : lifecycleControllers) {
			alc.onActivityStart();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		for (ActivityLifecycleController alc : lifecycleControllers) {
			alc.onActivityStop();
		}
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
		showNewScreenFilterWarning();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (dialogFrag != null) {
			dialogFrag.dismiss();
			dialogFrag = null;
		}
	}

	protected void showNewScreenFilterWarning() {
		final Set<String> apps = screenFilterMonitor.getApps();
		if (apps.isEmpty()) {
			return;
		}
		dialogFrag = SFDialogFragment.newInstance(new ArrayList<>(apps));
		dialogFrag.setCancelable(false);
		dialogFrag.show(getSupportFragmentManager(), "SFDialog");
	}

	public void rememberShownApps(ArrayList<String> s, boolean permanent) {
		screenFilterMonitor.storeAppsAsShown(s, permanent);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		destroyed = true;
		for (ActivityLifecycleController alc : lifecycleControllers) {
			alc.onActivityDestroy();
		}
	}

	@Override
	public void runOnUiThreadUnlessDestroyed(final Runnable r) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (!destroyed && !isFinishing()) r.run();
			}
		});
	}

	public void showSoftKeyboard(View view) {
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).showSoftInput(view, SHOW_IMPLICIT);
	}

	public void hideSoftKeyboard(View view) {
		IBinder token = view.getWindowToken();
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).hideSoftInputFromWindow(token, 0);
	}

	@UiThread
	public void handleDbException(DbException e) {
		supportFinishAfterTransition();
	}

}
