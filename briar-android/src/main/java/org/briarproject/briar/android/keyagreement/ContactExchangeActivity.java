package org.briarproject.briar.android.keyagreement;

import android.os.Bundle;
import android.support.annotation.UiThread;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactExchangeManager;
import org.briarproject.bramble.api.contact.event.ContactExchangeFailedEvent;
import org.briarproject.bramble.api.contact.event.ContactExchangeSucceededEvent;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;

import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactExchangeActivity extends KeyAgreementActivity implements
		EventListener {

	@Inject
	@IoExecutor
	Executor ioExecutor;

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile ContactExchangeManager contactExchangeManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		getSupportActionBar().setTitle(R.string.add_contact_title);
	}

	@Override
	public void onStart() {
		super.onStart();
		// Listen to updates from ContactExchangeManager
		eventBus.addListener(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		// Stop listening to updates from ContactExchangeManager
		eventBus.addListener(this);
	}

	private void startContactExchange(KeyAgreementResult result) {
		ioExecutor.execute(() -> {
			// Exchange contact details
			contactExchangeManager.exchangeContacts(result.getTransportId(),
					result.getConnection(), result.getMasterKey(),
					result.wasAlice());
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactExchangeSucceededEvent) {
			ContactExchangeSucceededEvent c = (ContactExchangeSucceededEvent) e;
			contactExchangeSucceeded(c.getRemoteAuthor());
		} else if (e instanceof ContactExchangeFailedEvent) {
			ContactExchangeFailedEvent c = (ContactExchangeFailedEvent) e;
			if (c.wasDuplicateContact()) {
				duplicateContact(requireNonNull(c.getDuplicateRemoteAuthor()));
			} else {
				contactExchangeFailed();
			}
		}
	}

	@UiThread
	private void contactExchangeSucceeded(Author remoteAuthor) {
		String contactName = remoteAuthor.getName();
		String format = getString(R.string.contact_added_toast);
		String text = String.format(format, contactName);
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
		showErrorFragment(R.string.connection_error_explanation);
	}

	@UiThread
	@Override
	public void keyAgreementFailed() {
		showErrorFragment(R.string.connection_error_explanation);
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
		showErrorFragment(R.string.connection_error_explanation);
	}

	@UiThread
	@Override
	public String keyAgreementFinished(KeyAgreementResult result) {
		startContactExchange(result);
		return getString(R.string.exchanging_contact_details);
	}
}
