package org.briarproject.briar.android.contact;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.app.AlarmManager.ELAPSED_REALTIME;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.EXTRA_TEXT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.os.SystemClock.elapsedRealtime;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.StringUtils.getRandomBase32String;

public class ContactLinkExchangeActivity extends BriarActivity implements
		BaseFragmentListener {

	private static final Logger LOG =
			Logger.getLogger(ContactLinkExchangeActivity.class.getName());

	static final String OUR_LINK = "briar://" + getRandomBase32String(128);

	@Inject
	LifecycleManager lifecycleManager;
	@Inject
	MessagingManager messagingManager;
	@Inject
	Clock clock;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_fragment_container);

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayHomeAsUpEnabled(true);
		}

		Intent i = getIntent();
		if (i != null) {
			String action = i.getAction();
			if (ACTION_SEND.equals(action) || ACTION_VIEW.equals(action)) {
				String text = i.getStringExtra(EXTRA_TEXT);
				if (text != null) {
					showInitialFragment(
							ContactLinkExchangeFragment.newInstance(text));
					return;
				}
				String uri = i.getDataString();
				if (uri != null) {
					showInitialFragment(
							ContactLinkExchangeFragment.newInstance(uri));
					return;
				}
			} else if ("addContact".equals(action)) {
				removeFakeRequest(i.getStringExtra("name"),
						i.getLongExtra("timestamp", 0));
				setIntent(null);
				finish();
			}
		}
		if (state == null) {
			showInitialFragment(new ContactLinkExchangeFragment());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	boolean isBriarLink(CharSequence s) {
		String link = s.toString().trim();
		return link.matches("^(briar://)?[a-z2-7]{64}$");
	}

	void scanCode() {
		showNextFragment(new ContactQrCodeInputFragment());
	}

	void linkScanned(@Nullable String link) {
		// FIXME: Contact name is lost
		showNextFragment(ContactLinkExchangeFragment.newInstance(link));
	}

	void showCode() {
		showNextFragment(new ContactQrCodeOutputFragment());
	}

	void addFakeRequest(String name, String link) {
		long timestamp = clock.currentTimeMillis();
		try {
			messagingManager.addNewPendingContact(name, timestamp);
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}

		AlarmManager alarmManager =
				(AlarmManager) requireNonNull(getSystemService(ALARM_SERVICE));
		double random = getPseudoRandom(link, OUR_LINK);
		long m = MINUTES.toMillis(1);
		long fromNow = (long) (-m * Math.log(random));
		LOG.info("Delay " + fromNow + " ms based on seed " + random);
		long triggerAt = elapsedRealtime() + fromNow;

		Intent i = new Intent(this, ContactLinkExchangeActivity.class);
		i.setAction("addContact");
		i.setFlags(FLAG_ACTIVITY_NEW_TASK);
		i.putExtra("name", name);
		i.putExtra("timestamp", timestamp);
		PendingIntent pendingIntent =
				PendingIntent.getActivity(this, (int) timestamp / 1000, i, 0);
		alarmManager.set(ELAPSED_REALTIME, triggerAt, pendingIntent);
	}

	/**
	 * Returns a pseudo-random value greater than or equal to 0 and less than 1,
	 * approximately uniformly distributed, based on the given strings. The
	 * same value is returned if the strings are swapped.
	 */
	private double getPseudoRandom(String a, String b) {
		String first, second;
		if (CASE_INSENSITIVE_ORDER.compare(a, b) < 0) {
			first = a;
			second = b;
		} else {
			first = b;
			second = a;
		}
		int hash = (first + second).hashCode() & Integer.MAX_VALUE;
		return hash / (1.0 + Integer.MAX_VALUE);
	}

	private void removeFakeRequest(String name, long timestamp) {
		if (lifecycleManager.getLifecycleState() != RUNNING) {
			LOG.info("Lifecycle not started, not adding contact " + name);
			return;
		}
		LOG.info("Adding Contact " + name);
		try {
			messagingManager.removePendingContact(name, timestamp);
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}
}
