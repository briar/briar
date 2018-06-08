package org.briarproject.briar.android.activity;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.LayoutRes;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.AndroidComponent;
import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.android.DestroyableContext;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.controller.ActivityLifecycleController;
import org.briarproject.briar.android.forum.ForumModule;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.ScreenFilterDialogFragment;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.android.widget.TapSafeFrameLayout;
import org.briarproject.briar.android.widget.TapSafeFrameLayout.OnTapFilteredListener;
import org.briarproject.briar.api.android.ScreenFilterMonitor;
import org.briarproject.briar.api.android.ScreenFilterMonitor.AppDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static org.briarproject.briar.android.TestingConstants.PREVENT_SCREENSHOTS;

public abstract class BaseActivity extends AppCompatActivity
		implements DestroyableContext, OnTapFilteredListener {

	@Inject
	protected ScreenFilterMonitor screenFilterMonitor;

	protected ActivityComponent activityComponent;

	private final List<ActivityLifecycleController> lifecycleControllers =
			new ArrayList<>();
	private boolean destroyed = false;

	@Nullable
	private Toolbar toolbar = null;
	private boolean searchedForToolbar = false;

	public abstract void injectActivity(ActivityComponent component);

	public void addLifecycleController(ActivityLifecycleController alc) {
		lifecycleControllers.add(alc);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

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

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(
				Localizer.getInstance().setLocale(base));
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
		protectToolbar();
		ScreenFilterDialogFragment f = findDialogFragment();
		if (f != null) f.setDismissListener(this::protectToolbar);
	}

	@Nullable
	private ScreenFilterDialogFragment findDialogFragment() {
		Fragment f = getSupportFragmentManager().findFragmentByTag(
				ScreenFilterDialogFragment.TAG);
		return (ScreenFilterDialogFragment) f;
	}

	@Override
	protected void onStop() {
		super.onStop();
		for (ActivityLifecycleController alc : lifecycleControllers) {
			alc.onActivityStop();
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

	private boolean showScreenFilterWarning() {
		// If the dialog is already visible, filter the tap
		ScreenFilterDialogFragment f = findDialogFragment();
		if (f != null && f.isVisible()) return false;
		Collection<AppDetails> apps = screenFilterMonitor.getApps();
		// If all overlay apps have been allowed, allow the tap
		if (apps.isEmpty()) return true;
		// Show dialog unless onSaveInstanceState() has been called, see #1112
		FragmentManager fm = getSupportFragmentManager();
		if (!fm.isStateSaved()) {
			// Create dialog
			f = ScreenFilterDialogFragment.newInstance(apps);
			// When dialog is dismissed, update protection of toolbar
			f.setDismissListener(this::protectToolbar);
			// Hide soft keyboard when (re)showing dialog
			View focus = getCurrentFocus();
			if (focus != null) hideSoftKeyboard(focus);
			f.show(fm, ScreenFilterDialogFragment.TAG);
		}
		// Filter the tap
		return false;
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
	public void runOnUiThreadUnlessDestroyed(Runnable r) {
		runOnUiThread(() -> {
			if (!destroyed && !isFinishing()) r.run();
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

	/*
	 * Wraps the given view in a wrapper that notifies this activity when an
	 * obscured touch has been filtered, and returns the wrapper.
	 */
	private View makeTapSafeWrapper(View v) {
		TapSafeFrameLayout wrapper = new TapSafeFrameLayout(this);
		wrapper.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		wrapper.setOnTapFilteredListener(this);
		wrapper.addView(v);
		return wrapper;
	}

	/*
	 * Finds the AppCompat toolbar, if any, and configures it to filter
	 * obscured touches. If a custom toolbar is used, it will be part of the
	 * content view and thus protected by the wrapper. But the default toolbar
	 * is outside the wrapper.
	 */
	private void protectToolbar() {
		findToolbar();
		if (toolbar != null) {
			boolean filter = !screenFilterMonitor.getApps().isEmpty();
			UiUtils.setFilterTouchesWhenObscured(toolbar, filter);
		}
	}

	private void findToolbar() {
		if (searchedForToolbar) return;
		View decorView = getWindow().getDecorView();
		if (decorView instanceof ViewGroup)
			toolbar = findToolbar((ViewGroup) decorView);
		searchedForToolbar = true;
	}

	@Nullable
	private Toolbar findToolbar(ViewGroup vg) {
		// Views inside tap-safe layouts are already protected
		if (vg instanceof TapSafeFrameLayout) return null;
		for (int i = 0, len = vg.getChildCount(); i < len; i++) {
			View child = vg.getChildAt(i);
			if (child instanceof Toolbar) return (Toolbar) child;
			if (child instanceof ViewGroup) {
				Toolbar toolbar = findToolbar((ViewGroup) child);
				if (toolbar != null) return toolbar;
			}
		}
		return null;
	}

	@Override
	public void setContentView(@LayoutRes int layoutRes) {
		setContentView(getLayoutInflater().inflate(layoutRes, null));
	}

	@Override
	public void setContentView(View v) {
		super.setContentView(makeTapSafeWrapper(v));
	}

	@Override
	public void setContentView(View v, LayoutParams layoutParams) {
		super.setContentView(makeTapSafeWrapper(v), layoutParams);
	}

	@Override
	public void addContentView(View v, LayoutParams layoutParams) {
		super.addContentView(makeTapSafeWrapper(v), layoutParams);
	}

	@Override
	public boolean shouldAllowTap() {
		return showScreenFilterWarning();
	}
}
