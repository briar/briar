package org.briarproject.briar.android.contact.add.nearby;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState;

import javax.annotation.Nullable;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.ABORTED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.FAILED;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactExchangeActivity extends KeyAgreementActivity {

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		requireNonNull(getSupportActionBar())
				.setTitle(R.string.add_contact_title);
		viewModel.getKeyAgreementState()
				.observe(this, this::onKeyAgreementStateChanged);
		viewModel.getContactExchangeResult()
				.observe(this, this::onContactExchangeResult);
	}

	private void onKeyAgreementStateChanged(KeyAgreementState state) {
		if (state == ABORTED || state == FAILED) {
			showErrorFragment();
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

	private void showErrorFragment() {
		showNextFragment(new ContactExchangeErrorFragment());
	}
}
