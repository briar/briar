package org.briarproject.briar.android.contact;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog.Builder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.util.Random;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.app.AlarmManager.ELAPSED_REALTIME;
import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.EXTRA_TEXT;
import static android.os.SystemClock.elapsedRealtime;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class ContactLinkInputActivity extends BriarActivity
		implements TextWatcher {

	@Inject
	MessagingManager messagingManager;
	@Inject
	Clock clock;

	private ClipboardManager clipboard;
	private EditText linkInput;
	private Button pasteButton;
	private EditText contactNameInput;
	private Button addButton;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_contact_link_input);

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayHomeAsUpEnabled(true);
		}

		clipboard = (ClipboardManager) requireNonNull(
				getSystemService(CLIPBOARD_SERVICE));

		linkInput = findViewById(R.id.linkInput);
		linkInput.addTextChangedListener(this);

		pasteButton = findViewById(R.id.pasteButton);
		pasteButton.setOnClickListener(v -> linkInput
				.setText(clipboard.getPrimaryClip().getItemAt(0).getText()));

		contactNameInput = findViewById(R.id.contactNameInput);
		contactNameInput.addTextChangedListener(this);

		addButton = findViewById(R.id.addButton);
		addButton.setOnClickListener(v -> onAddButtonClicked());

		Intent i = getIntent();
		if (i != null) {
			String action = i.getAction();
			if (ACTION_SEND.equals(action) || ACTION_VIEW.equals(action)) {
				String text = i.getStringExtra(EXTRA_TEXT);
				if (text != null) linkInput.setText(text);
				String uri = i.getDataString();
				if (uri != null) linkInput.setText(uri);
			} else if ("addContact".equals(action)) {
				removeFakeRequest(i.getStringExtra("name"),
						i.getLongExtra("timestamp", 0));
				finish();
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (hasLinkInClipboard()) pasteButton.setEnabled(true);
		else pasteButton.setEnabled(false);
	}

	private boolean hasLinkInClipboard() {
		return clipboard.hasPrimaryClip() &&
				clipboard.getPrimaryClip().getDescription()
						.hasMimeType(MIMETYPE_TEXT_PLAIN) &&
				clipboard.getPrimaryClip().getItemCount() > 0;
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before,
			int count) {
		updateAddButtonState();
	}

	@Override
	public void afterTextChanged(Editable s) {
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

	private boolean isBriarLink(CharSequence s) {
		String link = s.toString().trim();
		return link.matches("^(briar://)?[A-Z2-7]{64}$");
	}

	private void updateAddButtonState() {
		addButton.setEnabled(isBriarLink(linkInput.getText()) &&
				contactNameInput.getText().length() > 0);
	}

	private void onAddButtonClicked() {
		addFakeRequest();

		Builder builder = new Builder(this, R.style.BriarDialogTheme_Neutral);
		builder.setMessage(getString(R.string.add_contact_link_question));
		builder.setPositiveButton(R.string.yes, (dialog, which) -> {
			startActivity(new Intent(ContactLinkInputActivity.this,
					NavDrawerActivity.class));
			finish();
		});
		builder.setNegativeButton(R.string.no, (dialog, which) -> {
			startActivity(new Intent(ContactLinkInputActivity.this,
					ContactLinkOutputActivity.class));
			finish();
		});
		builder.show();
	}

	private void addFakeRequest() {
		String name = contactNameInput.getText().toString();
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

		Intent i = new Intent(this, ContactLinkInputActivity.class);
		i.setAction("addContact");
		i.putExtra("name", name);
		i.putExtra("timestamp", timestamp);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 42, i, 0);
		alarmManager.set(ELAPSED_REALTIME, triggerAt, pendingIntent);

		Log.e("TEST", "Setting Alarm in " + MILLISECONDS.toSeconds(fromNow) +
				" seconds");
		Log.e("TEST", "with contact: " + name);
	}

	private void removeFakeRequest(String name, long timestamp) {
		Log.e("TEST", "Adding Contact " + name);
		try {
			messagingManager.removePendingContact(name, timestamp);
		} catch (DbException e) {
			e.printStackTrace();
		}
	}

}
