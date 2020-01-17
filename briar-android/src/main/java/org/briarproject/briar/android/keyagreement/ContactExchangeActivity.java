package org.briarproject.briar.android.keyagreement;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactExchangeActivity extends KeyAgreementActivity {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ContactExchangeViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		requireNonNull(getSupportActionBar())
				.setTitle(R.string.add_contact_title);
		viewModel = ViewModelProviders.of(this, viewModelFactory)
				.get(ContactExchangeViewModel.class);
	}

	private void startContactExchange(KeyAgreementResult result) {
		viewModel.getSucceeded().observe(this, succeeded -> {
			if (succeeded == null) return;
			if (succeeded) {
				Author remote = requireNonNull(viewModel.getRemoteAuthor());
				contactExchangeSucceeded(remote);
			} else {
				Author duplicate = viewModel.getDuplicateAuthor();
				if (duplicate == null) contactExchangeFailed();
				else duplicateContact(duplicate);
			}
		});
		viewModel.startContactExchange(result.getTransportId(),
				result.getConnection(), result.getMasterKey(),
				result.wasAlice());
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

	protected void showErrorFragment() {
		String errorMsg = getString(R.string.connection_error_explanation);
		BaseFragment f = ContactExchangeErrorFragment.newInstance(errorMsg);
		showNextFragment(f);
	}
}
