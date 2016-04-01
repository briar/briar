package org.briarproject.android;

import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.CallSuper;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.briarproject.android.event.AppBus;
import org.briarproject.android.event.ErrorEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;

import javax.inject.Inject;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;

public abstract class BaseActivity extends AppCompatActivity implements
		EventListener {

	protected ActivityComponent activityComponent;

	@Inject
	protected AppBus appBus;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		AndroidComponent applicationComponent =
				((BriarApplication) getApplication()).getApplicationComponent();

		activityComponent = DaggerActivityComponent.builder()
				.androidComponent(applicationComponent)
				.activityModule(new ActivityModule(this))
				.build();

		injectActivity(activityComponent);
	}

	@Override
	protected void onResume() {
		super.onResume();
		appBus.addListener(this);
	}

	@Override
	protected void onPause() {
		appBus.removeListener(this);
		super.onPause();
	}

	@Override
	@CallSuper
	public void eventOccurred(Event e) {
		if (e instanceof ErrorEvent) {
			finish();
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
