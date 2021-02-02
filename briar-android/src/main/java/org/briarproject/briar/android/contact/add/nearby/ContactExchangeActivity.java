package org.briarproject.briar.android.contact.add.nearby;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.ContactExchangeFinished;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.Failed;

import javax.annotation.Nullable;

import androidx.appcompat.widget.Toolbar;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactExchangeActivity extends KeyAgreementActivity {

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		requireNonNull(getSupportActionBar())
				.setTitle(R.string.add_contact_title);
		viewModel.getState()
				.observe(this, this::onContactAddingStateChanged);
	}

	@Override
	public void onBackPressed() {
		if (viewModel.getState().getValue() instanceof Failed) {
			// finish this activity when going back in failed state
			supportFinishAfterTransition();
		} else {
			super.onBackPressed();
		}
	}

	private void onContactAddingStateChanged(ContactAddingState state) {
		if (state instanceof ContactExchangeFinished) {
			ContactExchangeResult result =
					((ContactExchangeFinished) state).result;
			onContactExchangeResult(result);
		} else if (state instanceof Failed) {
			// Remove navigation icon, so user can't go back when failed
			// ErrorFragment will finish or relaunch this activity
			Toolbar toolbar = findViewById(R.id.toolbar);
			toolbar.setNavigationIcon(null);

			Boolean qrCodeTooOld = ((Failed) state).qrCodeTooOld;
			onAddingContactFailed(qrCodeTooOld);
		}
	}

	private void onContactExchangeResult(ContactExchangeResult result) {
		if (result instanceof ContactExchangeResult.Success) {
			Author remoteAuthor =
					((ContactExchangeResult.Success) result).remoteAuthor;
			String contactName = remoteAuthor.getName();
			String text = getString(R.string.contact_added_toast, contactName);
			Toast.makeText(this, text, LENGTH_LONG).show();
			supportFinishAfterTransition();
		} else if (result instanceof ContactExchangeResult.Error) {
			Author duplicateAuthor =
					((ContactExchangeResult.Error) result).duplicateAuthor;
			if (duplicateAuthor == null) {
				showErrorFragment();
			} else {
				String contactName = duplicateAuthor.getName();
				String text =
						getString(R.string.contact_already_exists, contactName);
				Toast.makeText(this, text, LENGTH_LONG).show();
				supportFinishAfterTransition();
			}
		} else throw new AssertionError();
	}

	private void onAddingContactFailed(@Nullable Boolean qrCodeTooOld) {
		if (qrCodeTooOld == null) {
			showErrorFragment();
		} else {
			String msg;
			if (qrCodeTooOld) {
				msg = getString(R.string.qr_code_too_old,
						getString(R.string.app_name));
			} else {
				msg = getString(R.string.qr_code_too_new,
						getString(R.string.app_name));
			}
			showNextFragment(ContactExchangeErrorFragment.newInstance(msg));
		}
	}

	private void showErrorFragment() {
		showNextFragment(new ContactExchangeErrorFragment());
	}
}
