package org.briarproject.briar.android.login;

import android.content.Intent;
import android.os.Bundle;
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

import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.handler.UiResultHandler;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.android.util.UiUtils;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;

public class SetupActivity extends BaseActivity implements OnClickListener,
		OnEditorActionListener {

	@Inject
	SetupController setupController;

	private TextInputLayout nicknameEntryWrapper;
	private TextInputLayout passwordEntryWrapper;
	private TextInputLayout passwordConfirmationWrapper;
	private EditText nicknameEntry;
	private EditText passwordEntry;
	private EditText passwordConfirmation;
	private StrengthMeter strengthMeter;
	private Button createAccountButton;
	private ProgressBar progress;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		// fade-in after splash screen instead of default animation
		overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
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
		else strengthMeter.setVisibility(GONE);
		String nickname = nicknameEntry.getText().toString();
		int nicknameLength = StringUtils.toUtf8(nickname).length;
		String firstPassword = passwordEntry.getText().toString();
		String secondPassword = passwordConfirmation.getText().toString();
		boolean passwordsMatch = firstPassword.equals(secondPassword);
		float strength =
				setupController.estimatePasswordStrength(firstPassword);
		strengthMeter.setStrength(strength);
		UiUtils.setError(nicknameEntryWrapper,
				getString(R.string.name_too_long),
				nicknameLength > MAX_AUTHOR_NAME_LENGTH);
		UiUtils.setError(passwordEntryWrapper,
				getString(R.string.password_too_weak),
				firstPassword.length() > 0 && strength < QUITE_WEAK);
		UiUtils.setError(passwordConfirmationWrapper,
				getString(R.string.passwords_do_not_match),
				secondPassword.length() > 0 && !passwordsMatch);
		createAccountButton.setEnabled(nicknameLength > 0
				&& nicknameLength <= MAX_AUTHOR_NAME_LENGTH
				&& passwordsMatch && strength >= QUITE_WEAK);
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		hideSoftKeyboard(v);
		return true;
	}

	@Override
	public void onClick(View view) {
		// Replace the button with a progress bar
		createAccountButton.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		String nickname = nicknameEntry.getText().toString();
		String password = passwordEntry.getText().toString();

		setupController.storeAuthorInfo(nickname, password,
				new UiResultHandler<Void>(this) {
					@Override
					public void onResultUi(Void result) {
						showMain();
					}
				});
	}

	private void showMain() {
		Intent i = new Intent(this, NavDrawerActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);
		supportFinishAfterTransition();
		overridePendingTransition(R.anim.screen_new_in, R.anim.screen_old_out);
	}
}
