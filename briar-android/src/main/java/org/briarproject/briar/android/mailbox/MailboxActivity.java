package org.briarproject.briar.android.mailbox;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.util.UiUtils.showFragment;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class MailboxActivity extends BriarActivity {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private MailboxViewModel viewModel;
	private ProgressBar progressBar;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(MailboxViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mailbox);

		progressBar = findViewById(R.id.progressBar);
		if (viewModel.getState().getValue() == null) {
			progressBar.setVisibility(VISIBLE);
		}

		viewModel.getState().observe(this, state -> {
			if (state instanceof MailboxState.NotSetup) {
				onNotSetup();
			} else if (state instanceof MailboxState.SettingUp) {
				onCodeScanned();
			} else if (state instanceof MailboxState.QrCodeWrong) {
				onQrCodeWrong();
			} else if (state instanceof MailboxState.OfflineInSetup) {
				onOffline();
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		if (viewModel.getState()
				.getValue() instanceof MailboxState.SettingUp) {
			// don't go back in flow if we are already setting up mailbox
			supportFinishAfterTransition();
		} else {
			super.onBackPressed();
		}
	}

	private void onNotSetup() {
		progressBar.setVisibility(INVISIBLE);
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, new SetupIntroFragment(),
						SetupIntroFragment.TAG)
				.commit();
	}

	private void onCodeScanned() {
		showFragment(getSupportFragmentManager(),
				new MailboxConnectingFragment(),
				MailboxConnectingFragment.TAG, false);
	}

	private void onQrCodeWrong() {
		Fragment f = ErrorFragment.newInstance(
				R.string.mailbox_setup_qr_code_wrong_title,
				R.string.mailbox_setup_qr_code_wrong_description);
		showFragment(getSupportFragmentManager(), f, ErrorFragment.TAG);
	}

	private void onOffline() {
		showFragment(getSupportFragmentManager(), new OfflineFragment(),
				OfflineFragment.TAG);
	}

}
