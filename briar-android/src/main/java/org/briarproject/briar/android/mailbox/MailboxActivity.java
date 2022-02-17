package org.briarproject.briar.android.mailbox;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxPairingState.Pairing;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
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
		if (s instanceof MailboxState.Pairing &&
				((MailboxState.Pairing) s).pairingState instanceof Pairing) {
			// don't go back in flow if we are already pairing with the mailbox
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
		showFragment(getSupportFragmentManager(), new MailboxScanFragment(),
				MailboxScanFragment.TAG);
	}

	private void onMailboxPairingStateChanged(MailboxPairingState s) {
		progressBar.setVisibility(INVISIBLE);
		Fragment f;
		String tag;
		boolean addToBackStack = true;
		if (s instanceof MailboxPairingState.QrCodeReceived) {
			// ignore, showing yet another progress fragment messes with back stack
			return;
		} else if (s instanceof MailboxPairingState.Pairing) {
			f = new MailboxConnectingFragment();
			tag = MailboxConnectingFragment.TAG;
			addToBackStack = false;
		} else if (s instanceof MailboxPairingState.InvalidQrCode) {
			f = ErrorFragment.newInstance(
					R.string.mailbox_setup_qr_code_wrong_title,
					R.string.mailbox_setup_qr_code_wrong_description);
			tag = ErrorFragment.TAG;
		} else if (s instanceof MailboxPairingState.MailboxAlreadyPaired) {
			// TODO
			Toast.makeText(this, "MailboxAlreadyPaired", LENGTH_LONG).show();
			return;
		} else if (s instanceof MailboxPairingState.ConnectionError) {
			// TODO
			Toast.makeText(this, "Connection Error", LENGTH_LONG).show();
			return;
		} else if (s instanceof MailboxPairingState.AssertionError) {
			// TODO
			Toast.makeText(this, "Connection Error", LENGTH_LONG).show();
			return;
		} else if (s instanceof MailboxPairingState.Paired) {
			// TODO
			Toast.makeText(this, "Connection Error", LENGTH_LONG).show();
			return;
		} else {
			throw new IllegalStateException("Unhandled state: " + s.getClass());
		}
		showFragment(getSupportFragmentManager(), f, tag, addToBackStack);
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

}
