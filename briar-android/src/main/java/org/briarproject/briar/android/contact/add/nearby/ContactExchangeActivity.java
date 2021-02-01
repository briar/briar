package org.briarproject.briar.android.contact.add.nearby;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.annotation.Nullable;

import androidx.annotation.UiThread;

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
	}

	private void startContactExchange(KeyAgreementResult agreementResult) {
		viewModel.getContactExchangeResult().observe(this, result -> {
			if (result instanceof ContactExchangeResult.Success) {
				Author remoteAuthor =
						((ContactExchangeResult.Success) result).remoteAuthor;
				contactExchangeSucceeded(remoteAuthor);
			} else if (result instanceof ContactExchangeResult.Error) {
				Author duplicateAuthor =
						((ContactExchangeResult.Error) result).duplicateAuthor;
				if (duplicateAuthor == null) contactExchangeFailed();
				else duplicateContact(duplicateAuthor);
			} else throw new AssertionError();
		});
		viewModel.startContactExchange(agreementResult.getTransportId(),
				agreementResult.getConnection(), agreementResult.getMasterKey(),
				agreementResult.wasAlice());
	}

	@UiThread
	private void contactExchangeSucceeded(Author remoteAuthor) {
		String contactName = remoteAuthor.getName();
		String text = getString(R.string.contact_added_toast, contactName);
		Toast.makeText(this, text, LENGTH_LONG).show();
		supportFinishAfterTransition();
	}

	@UiThread
	private void duplicateContact(Author remoteAuthor) {
		String contactName = remoteAuthor.getName();
		String format = getString(R.string.contact_already_exists);
		String text = String.format(format, contactName);
		Toast.makeText(this, text, LENGTH_LONG).show();
		finish();
	}

	@UiThread
	private void contactExchangeFailed() {
		showErrorFragment();
	}

	@UiThread
	@Override
	public void keyAgreementFailed() {
		showErrorFragment();
	}

	@UiThread
	@Override
	public String keyAgreementWaiting() {
		return getString(R.string.waiting_for_contact_to_scan);
	}

	@UiThread
	@Override
	public String keyAgreementStarted() {
		return getString(R.string.authenticating_with_device);
	}

	@UiThread
	@Override
	public void keyAgreementAborted(boolean remoteAborted) {
		showErrorFragment();
	}

	@UiThread
	@Override
	public String keyAgreementFinished(KeyAgreementResult result) {
		startContactExchange(result);
		return getString(R.string.exchanging_contact_details);
	}

	private void showErrorFragment() {
		showNextFragment(new ContactExchangeErrorFragment());
	}
}
