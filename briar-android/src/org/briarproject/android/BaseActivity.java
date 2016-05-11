package org.briarproject.android;

import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.briarproject.android.controller.ActivityLifecycleController;

import java.util.ArrayList;
import java.util.List;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;

public abstract class BaseActivity extends AppCompatActivity {

	protected ActivityComponent activityComponent;

	private List<ActivityLifecycleController> lifecycleControllers =
			new ArrayList<ActivityLifecycleController>();

	public void addLifecycleController(
			ActivityLifecycleController lifecycleController) {
		this.lifecycleControllers.add(lifecycleController);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		AndroidComponent applicationComponent =
				((BriarApplication) getApplication()).getApplicationComponent();

		activityComponent = DaggerActivityComponent.builder()
				.androidComponent(applicationComponent)
				.activityModule(getActivityModule())
				.build();

		injectActivity(activityComponent);

		for (ActivityLifecycleController alc : lifecycleControllers) {
			alc.onActivityCreate();
		}
	}

	// This exists to make test overrides easier
	protected ActivityModule getActivityModule() {
		return new ActivityModule(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		for (ActivityLifecycleController alc : lifecycleControllers) {
			alc.onActivityResume();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		for (ActivityLifecycleController alc : lifecycleControllers) {
			alc.onActivityPause();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		for (ActivityLifecycleController alc : lifecycleControllers) {
			alc.onActivityDestroy();
		}
	}

	public abstract void injectActivity(ActivityComponent component);

	protected void showSoftKeyboard(View view) {
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).showSoftInput(view, SHOW_IMPLICIT);
	}

	public void hideSoftKeyboard(View view) {
		IBinder token = view.getWindowToken();
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).hideSoftInputFromWindow(token, 0);
	}

}
