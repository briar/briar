package org.briarproject.android;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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

import org.briarproject.R;
import org.briarproject.android.controller.EncryptedKeyNullException;
import org.briarproject.android.controller.PasswordController;
import org.briarproject.android.controller.ResultHandler;
import org.briarproject.android.util.AndroidUtils;

import javax.inject.Inject;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class PasswordActivity extends BaseActivity {

	private Button signInButton;
	private ProgressBar progress;
	private TextInputLayout input;
	private EditText password;

	@Inject
	PasswordController passwordHelper;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		if (!passwordHelper.initialized()) {
			clearSharedPrefsAndDeleteEverything();
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
				if (count > 0) AndroidUtils.setError(input, null, false);
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
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

	private void clearSharedPrefsAndDeleteEverything() {
		passwordHelper.clearPrefs();
		AndroidUtils.deleteAppData(this);
		setResult(RESULT_CANCELED);
		startActivity(new Intent(this, SetupActivity.class));
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
		builder.setNegativeButton(R.string.cancel_button, null);
		builder.setPositiveButton(R.string.delete_button,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						clearSharedPrefsAndDeleteEverything();
					}
				});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void validatePassword() {
		hideSoftKeyboard(password);
		signInButton.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		passwordHelper.validatePassword(password.getText().toString(),
				new ResultHandler<Boolean, EncryptedKeyNullException>() {
					@Override
					public void onResult(Boolean result) {
						if (result != null && result) {
							setResult(RESULT_OK);
							finish();
						} else {
							tryAgain();
						}
					}

					@Override
					public void onException(EncryptedKeyNullException e) {
						// TODO ?
					}
				});
	}

	private void tryAgain() {
		AndroidUtils.setError(input, getString(R.string.try_again),
				true);
		signInButton.setVisibility(VISIBLE);
		progress.setVisibility(INVISIBLE);
		password.setText("");

		// show the keyboard again
		showSoftKeyboard(password);
	}

}
