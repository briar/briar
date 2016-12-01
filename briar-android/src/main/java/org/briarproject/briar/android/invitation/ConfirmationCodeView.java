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

class ConfirmationCodeView extends AddContactView {

	public enum ConfirmationState { CONNECTED, ENTER_CODE, WAIT_FOR_CONTACT, DETAILS }
	private ConfirmationState state;

	ConfirmationCodeView(Context ctx) {
		super(ctx);
		this.state = ConfirmationState.ENTER_CODE;
	}

	ConfirmationCodeView(Context ctx, ConfirmationState state) {
		super(ctx);
		this.state = state;
	}

	@Override
	void populate() {
		removeAllViews();
		Context ctx = getContext();

		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService
				(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.invitation_bluetooth_confirmation_code, this);

		// local confirmation code
		TextView code = (TextView) view.findViewById(R.id.codeView);
		int localCode = container.getLocalConfirmationCode();
		code.setText(String.format("%06d", localCode));

		if (state != ConfirmationState.ENTER_CODE) {
			// hide views we no longer need
			view.findViewById(R.id.enterCodeTextView).setVisibility(View.GONE);
			view.findViewById(R.id.codeEntryView).setVisibility(View.GONE);
			view.findViewById(R.id.continueButton).setVisibility(View.GONE);

			// show progress indicator
			view.findViewById(R.id.progressBar).setVisibility(View.VISIBLE);

			// show what we are waiting for
			TextView connecting = (TextView) view.findViewById(R.id.waitingView);
			int textId;
			if (state == ConfirmationState.CONNECTED) {
				textId = R.string.calculating_confirmation_code;
				view.findViewById(R.id.yourConfirmationCodeView).setVisibility(View.GONE);
				view.findViewById(R.id.codeView).setVisibility(View.GONE);
			} else if (state == ConfirmationState.WAIT_FOR_CONTACT) {
				textId = R.string.waiting_for_contact;
			} else {
				textId = R.string.exchanging_contact_details;
			}
			connecting.setText(ctx.getString(textId));
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
		container.remoteConfirmationCodeEntered(code);

		// Hide the soft keyboard
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).hideSoftInputFromWindow(codeEntry.getWindowToken(), 0);
	}

}
