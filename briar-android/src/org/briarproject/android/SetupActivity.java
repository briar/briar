package org.briarproject.android;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.briarproject.R;
import org.briarproject.android.controller.SetupController;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.StrengthMeter;
import org.briarproject.util.StringUtils;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;

public class SetupActivity extends BaseActivity implements OnClickListener,
		OnEditorActionListener {

	@Inject
	SetupController setupController;

	TextInputLayout nicknameEntryWrapper;
	TextInputLayout passwordEntryWrapper;
	TextInputLayout passwordConfirmationWrapper;
	EditText nicknameEntry;
	EditText passwordEntry;
	EditText passwordConfirmation;
	StrengthMeter strengthMeter;
	Button createAccountButton;
	ProgressBar progress;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_setup);

		nicknameEntryWrapper =
				(TextInputLayout) findViewById(R.id.nickname_entry_wrapper);
		passwordEntryWrapper =
				(TextInputLayout) findViewById(R.id.password_entry_wrapper);
		passwordConfirmationWrapper =
				(TextInputLayout) findViewById(R.id.password_confirm_wrapper);
		nicknameEntry = (EditText) findViewById(R.id.nickname_entry);
		passwordEntry = (EditText) findViewById(R.id.password_entry);
		passwordConfirmation = (EditText) findViewById(R.id.password_confirm);
		strengthMeter = (StrengthMeter) findViewById(R.id.strength_meter);
		createAccountButton = (Button) findViewById(R.id.create_account);
		progress = (ProgressBar) findViewById(R.id.progress_wheel);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		TextWatcher tw = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				enableOrDisableContinueButton();
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		};

		nicknameEntry.addTextChangedListener(tw);
		passwordEntry.addTextChangedListener(tw);
		passwordConfirmation.addTextChangedListener(tw);
		passwordConfirmation.setOnEditorActionListener(this);
		createAccountButton.setOnClickListener(this);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void enableOrDisableContinueButton() {
		if (progress == null) return; // Not created yet
		if (passwordEntry.getText().length() > 0 && passwordEntry.hasFocus())
			strengthMeter.setVisibility(VISIBLE);
		else strengthMeter.setVisibility(INVISIBLE);
		String nickname = nicknameEntry.getText().toString();
		int nicknameLength = StringUtils.toUtf8(nickname).length;
		String firstPassword = passwordEntry.getText().toString();
		String secondPassword = passwordConfirmation.getText().toString();
		boolean passwordsMatch = firstPassword.equals(secondPassword);
		float strength = setupController.estimatePasswordStrength(firstPassword);
		strengthMeter.setStrength(strength);
		AndroidUtils.setError(nicknameEntryWrapper,
				getString(R.string.name_too_long),
				nicknameLength > MAX_AUTHOR_NAME_LENGTH);
		AndroidUtils.setError(passwordEntryWrapper,
				getString(R.string.password_too_weak),
				firstPassword.length() > 0 && strength < WEAK);
		AndroidUtils.setError(passwordConfirmationWrapper,
				getString(R.string.passwords_do_not_match),
				secondPassword.length() > 0 && !passwordsMatch);
		createAccountButton.setEnabled(nicknameLength > 0
				&& nicknameLength <= MAX_AUTHOR_NAME_LENGTH
				&& passwordsMatch && strength >= WEAK);
	}

	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		hideSoftKeyboard(v);
		return true;
	}

	public void onClick(View view) {
		// Replace the button with a progress bar
		createAccountButton.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		final String nickname = nicknameEntry.getText().toString();
		final String password = passwordEntry.getText().toString();
		setupController.createIdentity(nickname, password,
				new UiResultHandler<Long>(this) {
					@Override
					public void onResultUi(@NonNull Long result) {
						showMain(result);
					}
				});
	}

	private void showMain(final long handle) {
		Intent i = new Intent(SetupActivity.this,
				NavDrawerActivity.class);
		i.putExtra(BriarActivity.KEY_LOCAL_AUTHOR_HANDLE, handle);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);
		finish();
	}
}
