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
import org.briarproject.android.controller.handler.DestroyableContextManager;
import org.briarproject.android.controller.handler.UiContextResultHandler;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.StrengthMeter;
import org.briarproject.util.StringUtils;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;

public class SetupActivity extends BaseActivity implements OnClickListener,
		OnEditorActionListener {

	protected static final String TAG_SETUP = "briar.SETUP";

	@Inject
	protected SetupController setupController;

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

		if (containsContextResultHandler(TAG_SETUP)) {
			// Activity has been re-created due to an orientation change,
			// update the result handler with the current context and restore
			// the UI state
			createAccountButton.setVisibility(INVISIBLE);
			progress.setVisibility(VISIBLE);
		}
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

		setupController
				.storeAuthorInfo(nickname, password,
						new UiContextResultHandler<Void>(this, TAG_SETUP) {
							@Override
							public void onResultUi(@NonNull Void result,
									@NonNull DestroyableContextManager context) {
								((SetupActivity)context).showMain();
							}

						});
	}

	private void showMain() {
		Intent i = new Intent(this, NavDrawerActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);
		finish();
	}
}
