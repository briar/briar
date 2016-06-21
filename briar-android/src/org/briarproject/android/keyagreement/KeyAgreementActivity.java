package org.briarproject.android.keyagreement;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarFragmentActivity;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.util.CustomAnimations;
import org.briarproject.api.contact.ContactExchangeListener;
import org.briarproject.api.contact.ContactExchangeTask;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.KeyAgreementFinishedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.keyagreement.KeyAgreementResult;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;

public class KeyAgreementActivity extends BriarFragmentActivity implements
		BaseFragment.BaseFragmentListener,
		ChooseIdentityFragment.IdentitySelectedListener, EventListener,
		ContactExchangeListener {

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementActivity.class.getName());

	private static final String LOCAL_AUTHOR_ID = "briar.LOCAL_AUTHOR_ID";

	private static final int STEP_ID = 1;
	private static final int STEP_QR = 2;
	private static final int STEPS = 2;

	@Inject
	protected EventBus eventBus;

	private Toolbar toolbar;
	private View progressContainer;
	private TextView progressTitle;

	private AuthorId localAuthorId;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ContactExchangeTask contactExchangeTask;
	@Inject
	protected volatile IdentityManager identityManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_with_loading);

		toolbar = (Toolbar) findViewById(R.id.toolbar);
		progressContainer = findViewById(R.id.container_progress);
		progressTitle = (TextView) findViewById(R.id.title_progress_bar);

		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		if (state != null) {
			byte[] b = state.getByteArray(LOCAL_AUTHOR_ID);
			if (b != null)
				localAuthorId = new AuthorId(b);
		}

		showStep(localAuthorId == null ? STEP_ID : STEP_QR);
	}

	@SuppressWarnings("ConstantConditions")
	private void showStep(int step) {
		getSupportActionBar().setTitle(
				String.format(getString(R.string.add_contact_title_step), step,
						STEPS));
		switch (step) {
			case STEP_QR:
				startFragment(ShowQrCodeFragment.newInstance());
				break;
			case STEP_ID:
			default:
				startFragment(ChooseIdentityFragment.newInstance());
				break;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		eventBus.removeListener(this);
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		if (localAuthorId != null) {
			byte[] b = localAuthorId.getBytes();
			state.putByteArray(LOCAL_AUTHOR_ID, b);
		}
	}

	@Override
	public void showLoadingScreen(boolean isBlocking, int stringId) {
		if (isBlocking) {
			CustomAnimations.animateHeight(toolbar, false, 250);
		}
		progressTitle.setText(stringId);
		progressContainer.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideLoadingScreen() {
		CustomAnimations.animateHeight(toolbar, true, 250);
		progressContainer.setVisibility(View.INVISIBLE);
	}

	@Override
	public void identitySelected(AuthorId localAuthorId) {
		this.localAuthorId = localAuthorId;
		showStep(STEP_QR);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof KeyAgreementFinishedEvent) {
			KeyAgreementFinishedEvent event = (KeyAgreementFinishedEvent) e;
			keyAgreementFinished(event.getResult());
		}
	}

	private void keyAgreementFinished(final KeyAgreementResult result) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				showLoadingScreen(false, R.string.exchanging_contact_details);
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
					localAuthor = identityManager.getLocalAuthor(localAuthorId);
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
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				String contactName = remoteAuthor.getName();
				String format = getString(R.string.contact_added_toast);
				String text = String.format(format, contactName);
				Toast.makeText(KeyAgreementActivity.this, text, LENGTH_LONG)
						.show();
				finish();
			}
		});
	}

	@Override
	public void duplicateContact(final Author remoteAuthor) {
		runOnUiThread(new Runnable() {
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
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(KeyAgreementActivity.this,
						R.string.contact_exchange_failed, LENGTH_LONG).show();
				finish();
			}
		});
	}
}
