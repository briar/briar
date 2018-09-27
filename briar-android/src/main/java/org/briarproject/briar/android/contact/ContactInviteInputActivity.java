package org.briarproject.briar.android.contact;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.MenuItem;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.util.Random;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.app.AlarmManager.ELAPSED_REALTIME;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.EXTRA_TEXT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.os.SystemClock.elapsedRealtime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;

public class ContactInviteInputActivity extends BriarActivity implements
		BaseFragmentListener {

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
							ContactLinkInputFragment.newInstance(text));
					return;
				}
				String uri = i.getDataString();
				if (uri != null) {
					showInitialFragment(
							ContactLinkInputFragment.newInstance(uri));
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
			showInitialFragment(ContactLinkInputFragment.newInstance(null));
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
		return link.matches("^(briar://)?[A-Z2-7]{64}$");
	}

	void showLink(@Nullable String link) {
		showInitialFragment(ContactLinkInputFragment.newInstance(link));
	}

	void showCode() {
		showNextFragment(new ContactQrCodeInputFragment());
	}

	void showAlias() {
		showNextFragment(new ContactAliasInputFragment());
	}

	void addFakeRequest(String name) {
		long timestamp = clock.currentTimeMillis();
		try {
			messagingManager.addNewPendingContact(name, timestamp);
		} catch (DbException e) {
			e.printStackTrace();
		}

		AlarmManager alarmManager =
				(AlarmManager) requireNonNull(getSystemService(ALARM_SERVICE));
		long m = MINUTES.toMillis(1);
		long fromNow = (long) (-m * Math.log(new Random().nextDouble()));
		long triggerAt = elapsedRealtime() + fromNow;

		Intent i = new Intent(this, ContactInviteInputActivity.class);
		i.setAction("addContact");
		i.setFlags(FLAG_ACTIVITY_NEW_TASK);
		i.putExtra("name", name);
		i.putExtra("timestamp", timestamp);
		PendingIntent pendingIntent = PendingIntent
				.getActivity(this, (int) timestamp / 1000, i, 0);
		alarmManager.set(ELAPSED_REALTIME, triggerAt, pendingIntent);

		Log.e("TEST", "Setting Alarm in " + MILLISECONDS.toSeconds(fromNow) +
				" seconds");
		Log.e("TEST", "with contact: " + name);
	}

	private void removeFakeRequest(String name, long timestamp) {
		if (lifecycleManager.getLifecycleState() != RUNNING) {
			Log.e("TEST", "Lifecycle not started, not adding contact " + name);
			return;
		}
		Log.e("TEST", "Adding Contact " + name);
		try {
			messagingManager.removePendingContact(name, timestamp);
		} catch (DbException e) {
			e.printStackTrace();
		}
	}

}
