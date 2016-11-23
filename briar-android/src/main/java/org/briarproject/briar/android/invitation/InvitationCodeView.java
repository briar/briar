package org.briarproject.briar.android.invitation;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.briarproject.briar.R;

import static android.content.Context.INPUT_METHOD_SERVICE;

class InvitationCodeView extends AddContactView {

	private boolean waiting;

	InvitationCodeView(Context ctx, boolean waiting) {
		super(ctx);
		this.waiting = waiting;
	}

	InvitationCodeView(Context ctx) {
		this(ctx, false);
	}

	@Override
	void populate() {
		removeAllViews();
		Context ctx = getContext();

		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService
				(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.invitation_bluetooth_invitation_code, this);

		// local invitation code
		TextView code = (TextView) view.findViewById(R.id.codeView);
		int localCode = container.getLocalInvitationCode();
		code.setText(String.format("%06d", localCode));

		if (waiting) {
			// hide views we no longer need
			view.findViewById(R.id.enterCodeTextView).setVisibility(View.GONE);
			view.findViewById(R.id.codeEntryView).setVisibility(View.GONE);
			view.findViewById(R.id.continueButton).setVisibility(View.GONE);

			// show progress indicator
			view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);

			// show which code we are waiting for
			TextView connecting = (TextView) view.findViewById(R.id.waitingView);
			int remoteCode = container.getRemoteInvitationCode();
			String format = container.getString(R.string.searching_format);
			connecting.setText(String.format(format, remoteCode));
			connecting.setVisibility(View.VISIBLE);
		}
		else {
			// handle click on continue button
			final EditText codeEntry = (EditText) view.findViewById(R.id.codeEntryView);
			final Button continueButton = (Button) view.findViewById(R.id.continueButton);
			continueButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					send(codeEntry);
				}
			});

			// activate continue button only when we have a 6 digit (CODE_LEN) code
			codeEntry.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					continueButton.setEnabled(codeEntry.getText().length() == CODE_LEN);
				}

				@Override
				public void afterTextChanged(Editable s) {
				}
			});

			codeEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_GO && v.getText().length() == CODE_LEN) {
						send(v);
						return true;
					}
					return false;
				}
			});
		}
	}

	private void send(TextView codeEntry) {
		int code = Integer.parseInt(codeEntry.getText().toString());
		container.remoteInvitationCodeEntered(code);

		// Hide the soft keyboard
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).hideSoftInputFromWindow(codeEntry.getWindowToken(), 0);
	}

}
