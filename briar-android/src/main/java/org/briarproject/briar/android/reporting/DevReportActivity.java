package org.briarproject.briar.android.reporting;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;

import org.acra.dialog.BaseCrashReportDialog;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.logout.HideUiActivity;
import org.briarproject.briar.android.util.UserFeedback;

import java.io.File;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static java.util.Objects.requireNonNull;
import static org.acra.ACRAConstants.EXTRA_REPORT_FILE;
import static org.briarproject.briar.android.TestingConstants.PREVENT_SCREENSHOTS;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class DevReportActivity extends BaseCrashReportDialog {

	private AppCompatDelegate delegate;

	private AppCompatDelegate getDelegate() {
		if (delegate == null) {
			delegate = AppCompatDelegate.create(this, null);
		}
		return delegate;
	}

	@Override
	protected void preInit(@Nullable Bundle savedInstanceState) {
		super.preInit(savedInstanceState);
		getDelegate().installViewFactory();
		getDelegate().onCreate(savedInstanceState);
		if (getDelegate().applyDayNight()) {
			// If DayNight has been applied, we need to re-apply the theme for
			// the changes to take effect. On API 23+, we should bypass
			// setTheme(), which will no-op if the theme ID is identical to the
			// current theme ID.
			int theme = R.style.BriarTheme_NoActionBar;
			if (SDK_INT >= 23) {
				onApplyThemeResource(getTheme(), theme, false);
			} else {
				setTheme(theme);
			}
		}
	}

	@Override
	public void init(@Nullable Bundle state) {
		super.init(state);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		getDelegate().setContentView(R.layout.activity_dev_report);

		Toolbar toolbar = findViewById(R.id.toolbar);
		getDelegate().setSupportActionBar(toolbar);

		String title = getString(isFeedback() ? R.string.feedback_title :
				R.string.crash_report_title);
		requireNonNull(getDelegate().getSupportActionBar()).setTitle(title);

		if (state == null) showReportForm(isFeedback());
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(
				Localizer.getInstance().setLocale(base));
	}

	@Override
	public void onPostCreate(@Nullable Bundle state) {
		super.onPostCreate(state);
		getDelegate().onPostCreate(state);
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
		getDelegate().onPostResume();
	}

	@Override
	public void onTitleChanged(CharSequence title, int color) {
		super.onTitleChanged(title, color);
		getDelegate().setTitle(title);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		getDelegate().onConfigurationChanged(newConfig);
	}

	@Override
	public void onStop() {
		super.onStop();
		getDelegate().onStop();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getDelegate().onDestroy();
	}

	@Override
	public void onBackPressed() {
		closeReport();
	}

	void sendCrashReport(String comment, String email) {
		sendCrash(comment, email);
	}

	private boolean isFeedback() {
		return getException() instanceof UserFeedback;
	}

	void showReportForm(boolean showReportForm) {
		Fragment f;
		if (showReportForm) {
			File file =
					(File) getIntent().getSerializableExtra(EXTRA_REPORT_FILE);
			f = ReportFormFragment.newInstance(isFeedback(), file);
			requireNonNull(getDelegate().getSupportActionBar()).show();
		} else {
			f = new CrashFragment();
			requireNonNull(getDelegate().getSupportActionBar()).hide();
		}
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, f)
				.commit();
	}

	void closeReport() {
		cancelReports();
		Intent i = new Intent(this, HideUiActivity.class);
		i.addFlags(FLAG_ACTIVITY_NEW_TASK
				| FLAG_ACTIVITY_NO_ANIMATION
				| FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(i);
		finish();
	}

}
