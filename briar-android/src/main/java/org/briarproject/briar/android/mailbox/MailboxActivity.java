package org.briarproject.briar.android.mailbox;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.FinalFragment;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
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

		viewModel.getState().observeEvent(this, state -> {
			if (state instanceof MailboxState.NotSetup) {
				onNotSetup();
			} else if (state instanceof MailboxState.ScanningQrCode) {
				onScanningQrCode();
			} else if (state instanceof MailboxState.Pairing) {
				MailboxPairingState s =
						((MailboxState.Pairing) state).pairingState;
				onMailboxPairingStateChanged(s);
			} else if (state instanceof MailboxState.OfflineWhenPairing) {
				onOffline();
			} else if (state instanceof MailboxState.IsPaired) {
				onIsPaired(((MailboxState.IsPaired) state).mailboxStatus);
			} else {
				throw new AssertionError("Unknown state: " + state);
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
		MailboxState s = viewModel.getState().getLastValue();
		if (s instanceof MailboxState.Pairing) {
			// don't go back in the flow if we are already pairing
			// with the mailbox. We provide a try-again button instead.
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

	private void onScanningQrCode() {
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentByTag(MailboxScanFragment.TAG) != null) {
			// if the scanner is already on the back stack, pop back to it
			// instead of adding it to the stack again
			fm.popBackStackImmediate(MailboxScanFragment.TAG, 0);
		} else {
			showFragment(fm, new MailboxScanFragment(),
					MailboxScanFragment.TAG);
		}
	}

	private void onMailboxPairingStateChanged(MailboxPairingState s) {
		progressBar.setVisibility(INVISIBLE);
		FragmentManager fm = getSupportFragmentManager();
		Fragment f;
		String tag;
		if (s instanceof MailboxPairingState.QrCodeReceived) {
			// ignore, showing yet another progress fragment messes with back stack
			return;
		} else if (s instanceof MailboxPairingState.Pairing) {
			if (fm.getBackStackEntryCount() == 0) {
				// We re-launched into an existing state,
				// need to re-populate the back stack.
				repopulateBackStack();
			}
			f = new MailboxConnectingFragment();
			tag = MailboxConnectingFragment.TAG;
		} else if (s instanceof MailboxPairingState.InvalidQrCode) {
			f = ErrorFragment.newInstance(
					R.string.mailbox_setup_qr_code_wrong_title,
					R.string.mailbox_setup_qr_code_wrong_description);
			tag = ErrorFragment.TAG;
		} else if (s instanceof MailboxPairingState.MailboxAlreadyPaired) {
			f = ErrorFragment.newInstance(
					R.string.mailbox_setup_already_paired_title,
					R.string.mailbox_setup_already_paired_description);
			tag = ErrorFragment.TAG;
		} else if (s instanceof MailboxPairingState.ConnectionError) {
			f = ErrorFragment.newInstance(
					R.string.mailbox_setup_io_error_title,
					R.string.mailbox_setup_io_error_description);
			tag = ErrorFragment.TAG;
		} else if (s instanceof MailboxPairingState.UnexpectedError) {
			f = ErrorFragment.newInstance(
					R.string.mailbox_setup_assertion_error_title,
					R.string.mailbox_setup_assertion_error_description);
			tag = ErrorFragment.TAG;
		} else if (s instanceof MailboxPairingState.Paired) {
			f = FinalFragment.newInstance(R.string.mailbox_setup_paired_title,
					R.drawable.ic_check_circle_outline,
					R.color.briar_brand_green,
					R.string.mailbox_setup_paired_description);
			tag = FinalFragment.TAG;
		} else {
			throw new IllegalStateException("Unhandled state: " + s.getClass());
		}
		showFragment(fm, f, tag);
	}

	private void onOffline() {
		showFragment(getSupportFragmentManager(), new OfflineFragment(),
				OfflineFragment.TAG);
	}

	private void onIsPaired(MailboxStatus mailboxStatus) {
		progressBar.setVisibility(INVISIBLE);
		// TODO
		Toast.makeText(this, "NOT IMPLEMENTED", LENGTH_LONG).show();
	}

	private void repopulateBackStack() {
		FragmentManager fm = getSupportFragmentManager();
		onNotSetup();
		showFragment(fm, new SetupDownloadFragment(),
				SetupDownloadFragment.TAG);
		onScanningQrCode();
	}

}
