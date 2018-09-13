package org.briarproject.briar.android.login;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.BriarController;
import org.briarproject.briar.android.controller.handler.UiResultHandler;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.api.android.AndroidNotificationManager;

import javax.inject.Inject;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static org.briarproject.briar.android.util.UiUtils.enterPressed;

public class PasswordActivity extends BaseActivity {

	@Inject
	AccountManager accountManager;

	@Inject
	AndroidNotificationManager notificationManager;

	@Inject
	PasswordController passwordController;

	@Inject
	BriarController briarController;

	private Button signInButton;
	private ProgressBar progress;
	private TextInputLayout input;
	private EditText password;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		// fade-in after splash screen instead of default animation
		overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

		if (!accountManager.accountExists()) {
			setResult(RESULT_CANCELED);
			finish();
			return;
		}

		setContentView(R.layout.activity_password);
		signInButton = findViewById(R.id.btn_sign_in);
		progress = findViewById(R.id.progress_wheel);
		input = findViewById(R.id.password_layout);
		password = findViewById(R.id.edit_password);
		password.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == IME_ACTION_DONE || enterPressed(actionId, event)) {
				validatePassword();
				return true;
			}
			return false;
		});
		password.addTextChangedListener(new TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (count > 0) UiUtils.setError(input, null, false);
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		// If the user has already signed in, clean up this instance
		if (briarController.accountSignedIn()) {
			setResult(RESULT_OK);
			finish();
		} else {
			notificationManager.blockSignInNotification();
			notificationManager.clearSignInNotification();
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onBackPressed() {
		// Move task and activity to the background instead of showing another
		// password prompt. onActivityResult() won't be called in BriarActivity
		moveTaskToBack(true);
	}

	private void deleteAccount() {
		accountManager.deleteAccount();
		startActivity(new Intent(this, SetupActivity.class));
		setResult(RESULT_CANCELED);
		finish();
	}

	public void onSignInClick(View v) {
		validatePassword();
	}

	public void onForgottenPasswordClick(View v) {
		// TODO Encapsulate the dialog in a re-usable fragment
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
				R.style.BriarDialogTheme);
		builder.setTitle(R.string.dialog_title_lost_password);
		builder.setMessage(R.string.dialog_message_lost_password);
		builder.setPositiveButton(R.string.cancel, null);
		builder.setNegativeButton(R.string.delete,
				(dialog, which) -> deleteAccount());
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void validatePassword() {
		hideSoftKeyboard(password);
		signInButton.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		passwordController.validatePassword(password.getText().toString(),
				new UiResultHandler<Boolean>(this) {
					@Override
					public void onResultUi(@NonNull Boolean result) {
						if (result) {
							setResult(RESULT_OK);
							supportFinishAfterTransition();
							// don't show closing animation,
							// but one for opening NavDrawerActivity
							overridePendingTransition(R.anim.screen_new_in,
									R.anim.screen_old_out);
						} else {
							tryAgain();
						}
					}
				});
	}

	private void tryAgain() {
		UiUtils.setError(input, getString(R.string.try_again), true);
		signInButton.setVisibility(VISIBLE);
		progress.setVisibility(INVISIBLE);
		password.setText("");

		// show the keyboard again
		showSoftKeyboard(password);
	}
}
