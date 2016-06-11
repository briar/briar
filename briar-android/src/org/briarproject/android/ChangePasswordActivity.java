package org.briarproject.android;

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
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.controller.PasswordController;
import org.briarproject.android.controller.SetupController;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.StrengthMeter;

import javax.inject.Inject;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.WEAK;

public class ChangePasswordActivity extends BaseActivity
		implements OnClickListener,
		OnEditorActionListener {

	@Inject
	protected PasswordController passwordController;
	@Inject
	protected SetupController setupController;

	private TextInputLayout currentPasswordEntryWrapper;
	private TextInputLayout newPasswordEntryWrapper;
	private TextInputLayout newPasswordConfirmationWrapper;
	private EditText currentPassword;
	private EditText newPassword;
	private EditText newPasswordConfirmation;
	private StrengthMeter strengthMeter;
	private Button changePasswordButton;
	private ProgressBar progress;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_change_password);

		currentPasswordEntryWrapper =
				(TextInputLayout) findViewById(
						R.id.current_password_entry_wrapper);
		newPasswordEntryWrapper =
				(TextInputLayout) findViewById(R.id.new_password_entry_wrapper);
		newPasswordConfirmationWrapper =
				(TextInputLayout) findViewById(
						R.id.new_password_confirm_wrapper);
		currentPassword = (EditText) findViewById(R.id.current_password_entry);
		newPassword = (EditText) findViewById(R.id.new_password_entry);
		newPasswordConfirmation =
				(EditText) findViewById(R.id.new_password_confirm);
		strengthMeter = (StrengthMeter) findViewById(R.id.strength_meter);
		changePasswordButton = (Button) findViewById(R.id.change_password);
		progress = (ProgressBar) findViewById(R.id.progress_wheel);

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

		currentPassword.addTextChangedListener(tw);
		newPassword.addTextChangedListener(tw);
		newPasswordConfirmation.addTextChangedListener(tw);
		newPasswordConfirmation.setOnEditorActionListener(this);
		changePasswordButton.setOnClickListener(this);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void enableOrDisableContinueButton() {
		if (progress == null) return; // Not created yet
		if (newPassword.getText().length() > 0 && newPassword.hasFocus())
			strengthMeter.setVisibility(VISIBLE);
		else strengthMeter.setVisibility(INVISIBLE);
		String firstPassword = newPassword.getText().toString();
		String secondPassword = newPasswordConfirmation.getText().toString();
		boolean passwordsMatch = firstPassword.equals(secondPassword);
		float strength =
				setupController.estimatePasswordStrength(firstPassword);
		strengthMeter.setStrength(strength);
		AndroidUtils.setError(newPasswordEntryWrapper,
				getString(R.string.password_too_weak),
				firstPassword.length() > 0 && strength < WEAK);
		AndroidUtils.setError(newPasswordConfirmationWrapper,
				getString(R.string.passwords_do_not_match),
				secondPassword.length() > 0 && !passwordsMatch);
		changePasswordButton.setEnabled(
				!currentPassword.getText().toString().isEmpty() &&
						passwordsMatch && strength >= WEAK);
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		hideSoftKeyboard(v);
		return true;
	}

	@Override
	public void onClick(View view) {
		// Replace the button with a progress bar
		changePasswordButton.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		passwordController.changePassword(currentPassword.getText().toString(),
				newPassword.getText().toString(),
				new UiResultHandler<Boolean>(this) {
					@Override
					public void onResultUi(@NonNull Boolean result) {
						if (result) {
							Toast.makeText(ChangePasswordActivity.this,
									R.string.password_changed,
									Toast.LENGTH_LONG).show();
							setResult(RESULT_OK);
							finish();
						} else {
							tryAgain();
						}
					}
				});
	}

	private void tryAgain() {
		AndroidUtils.setError(currentPasswordEntryWrapper,
				getString(R.string.try_again), true);
		changePasswordButton.setVisibility(VISIBLE);
		progress.setVisibility(INVISIBLE);
		currentPassword.setText("");

		// show the keyboard again
		showSoftKeyboard(currentPassword);
	}
}
