package org.briarproject.briar.android.login;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.BriarController;
import org.briarproject.briar.android.controller.handler.UiResultHandler;
import org.briarproject.briar.android.util.UiUtils;

import javax.inject.Inject;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class PasswordActivity extends BaseActivity {

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

		if (!passwordController.accountExists()) {
			deleteAccount();
			return;
		}

		setContentView(R.layout.activity_password);
		signInButton = (Button) findViewById(R.id.btn_sign_in);
		progress = (ProgressBar) findViewById(R.id.progress_wheel);
		input = (TextInputLayout) findViewById(R.id.password_layout);
		password = (EditText) findViewById(R.id.edit_password);
		password.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				validatePassword();
				return true;
			}
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
		if (briarController.hasEncryptionKey()) {
			setResult(RESULT_OK);
			finish();
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onBackPressed() {
		// Show the home screen rather than another password prompt
		Intent intent = new Intent(ACTION_MAIN);
		intent.addCategory(CATEGORY_HOME);
		startActivity(intent);
	}

	private void deleteAccount() {
		passwordController.deleteAccount(this);
		setResult(RESULT_CANCELED);
		Intent i = new Intent(this, SetupActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(i);
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
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						deleteAccount();
					}
				});
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
