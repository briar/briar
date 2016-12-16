package org.briarproject.briar.android.keyagreement;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactExchangeListener;
import org.briarproject.bramble.api.contact.ContactExchangeTask;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFinishedEvent;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.keyagreement.IntroFragment.IntroScreenSeenListener;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class KeyAgreementActivity extends BriarActivity implements
		BaseFragmentListener, IntroScreenSeenListener, EventListener,
		ContactExchangeListener {

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementActivity.class.getName());

	@Inject
	EventBus eventBus;

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile ContactExchangeTask contactExchangeTask;
	@Inject
	volatile IdentityManager identityManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_fragment_container_toolbar);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		getSupportActionBar().setTitle(R.string.add_contact_title);
		if (state == null) {
			showInitialFragment(IntroFragment.newInstance());
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		eventBus.addListener(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		eventBus.removeListener(this);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void showNextScreen() {
		// FIXME #824
//		showNextFragment(ShowQrCodeFragment.newInstance());
		BaseFragment f = ShowQrCodeFragment.newInstance();
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, f, f.getUniqueTag())
				.addToBackStack(f.getUniqueTag())
				.commit();
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof KeyAgreementFinishedEvent) {
			KeyAgreementFinishedEvent event = (KeyAgreementFinishedEvent) e;
			keyAgreementFinished(event.getResult());
		}
	}

	private void keyAgreementFinished(final KeyAgreementResult result) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				startContactExchange(result);
			}
		});
	}

	private void startContactExchange(final KeyAgreementResult result) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				LocalAuthor localAuthor;
				// Load the local pseudonym
				try {
					localAuthor = identityManager.getLocalAuthor();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					contactExchangeFailed();
					return;
				}

				// Exchange contact details
				contactExchangeTask.startExchange(KeyAgreementActivity.this,
						localAuthor, result.getMasterKey(),
						result.getConnection(), result.getTransportId(),
						result.wasAlice());
			}
		});
	}

	@Override
	public void contactExchangeSucceeded(final Author remoteAuthor) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				String contactName = remoteAuthor.getName();
				String format = getString(R.string.contact_added_toast);
				String text = String.format(format, contactName);
				Toast.makeText(KeyAgreementActivity.this, text, LENGTH_LONG)
						.show();
				supportFinishAfterTransition();
			}
		});
	}

	@Override
	public void duplicateContact(final Author remoteAuthor) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				String contactName = remoteAuthor.getName();
				String format = getString(R.string.contact_already_exists);
				String text = String.format(format, contactName);
				Toast.makeText(KeyAgreementActivity.this, text, LENGTH_LONG)
						.show();
				finish();
			}
		});
	}

	@Override
	public void contactExchangeFailed() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(KeyAgreementActivity.this,
						R.string.contact_exchange_failed, LENGTH_LONG).show();
				finish();
			}
		});
	}

}
