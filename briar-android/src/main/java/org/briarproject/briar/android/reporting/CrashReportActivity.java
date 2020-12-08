package org.briarproject.briar.android.reporting;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.AndroidComponent;
import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.logout.HideUiActivity;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.TestingConstants.PREVENT_SCREENSHOTS;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CrashReportActivity extends AppCompatActivity {

	static final String EXTRA_THROWABLE = "throwable";
	static final String EXTRA_APP_START_TIME = "appStartTime";

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ReportViewModel viewModel;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);
		setContentView(R.layout.activity_dev_report);

		AndroidComponent androidComponent =
				((BriarApplication) getApplication()).getApplicationComponent();
		androidComponent.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(ReportViewModel.class);
		Intent intent = getIntent();
		Throwable t = (Throwable) intent.getSerializableExtra(EXTRA_THROWABLE);
		long appStartTime = intent.getLongExtra(EXTRA_APP_START_TIME, 0);
		viewModel.init(requireNonNull(t), appStartTime);
		viewModel.getShowReport().observeEvent(this, show -> {
			if (show) displayFragment(true);
		});
		viewModel.getCloseReport().observeEvent(this, res -> {
			if (res != 0) {
				Toast.makeText(this, res, LENGTH_LONG).show();
			}
			exit();
		});

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		if (savedInstanceState == null) displayFragment(viewModel.isFeedback());
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(Localizer.getInstance().setLocale(base));
	}

	@Override
	public void onBackPressed() {
		exit();
	}

	void displayFragment(boolean showReportForm) {
		Fragment f;
		if (showReportForm) {
			f = new ReportFormFragment();
			requireNonNull(getSupportActionBar()).show();
		} else {
			f = new CrashFragment();
			requireNonNull(getSupportActionBar()).hide();
		}
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, f, f.getTag())
				.commit();
	}

	void exit() {
		if (!viewModel.isFeedback()) {
			Intent i = new Intent(this, HideUiActivity.class);
			i.addFlags(FLAG_ACTIVITY_NEW_TASK
					| FLAG_ACTIVITY_NO_ANIMATION
					| FLAG_ACTIVITY_CLEAR_TASK);
			startActivity(i);
		}
		finish();
	}

}
