package org.briarproject.briar.android.contact;

import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.annotation.Nullable;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_TEXT;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.Objects.requireNonNull;

public class ContactLinkInputActivity extends BriarActivity
		implements TextWatcher {

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
		addButton.setOnClickListener(v -> Toast.makeText(this,
				"Contact " + contactNameInput.getText() + " requested",
				LENGTH_SHORT).show());

		Intent i = getIntent();
		if (i != null && ACTION_SEND.equals(i.getAction())) {
			String text = i.getStringExtra(EXTRA_TEXT);
			if (text != null) linkInput.setText(text);
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
		String regex = "^briar://[A-Z2-7]{64}$";
		return s.toString().trim().matches(regex);
	}

	private void updateAddButtonState() {
		addButton.setEnabled(isBriarLink(linkInput.getText()) &&
				contactNameInput.getText().length() > 0);
	}

}
