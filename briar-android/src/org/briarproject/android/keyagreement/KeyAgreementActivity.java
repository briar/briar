package org.briarproject.android.keyagreement;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarFragmentActivity;
import org.briarproject.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.android.keyagreement.IntroFragment.IntroScreenSeenListener;
import org.briarproject.android.util.CustomAnimations;
import org.briarproject.api.contact.ContactExchangeListener;
import org.briarproject.api.contact.ContactExchangeTask;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.KeyAgreementFinishedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.keyagreement.KeyAgreementResult;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;

public class KeyAgreementActivity extends BriarFragmentActivity implements
		BaseFragmentListener, IntroScreenSeenListener, EventListener,
		ContactExchangeListener {

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementActivity.class.getName());

	private static final int STEP_INTRO = 1;
	private static final int STEP_QR = 2;

	@Inject
	protected EventBus eventBus;

	private Toolbar toolbar;
	private View progressContainer;
	private TextView progressTitle;

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

		getSupportActionBar().setTitle(R.string.add_contact_title);
		if (state == null) showStep(STEP_INTRO);
	}

	private void showStep(int step) {
		switch (step) {
			case STEP_QR:
				startFragment(ShowQrCodeFragment.newInstance(), true);
				break;
			case STEP_INTRO:
			default:
				startFragment(IntroFragment.newInstance(), true);
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
	public void onBackPressed() {
		if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
			supportFinishAfterTransition();
		} else {
			super.onBackPressed();
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
	public void showNextScreen() {
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
