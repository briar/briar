package org.briarproject.android;

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

import org.briarproject.R;
import org.briarproject.android.controller.PasswordController;
import org.briarproject.android.controller.handler.DestroyableContextManager;
import org.briarproject.android.controller.handler.UiContextResultHandler;
import org.briarproject.android.util.AndroidUtils;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class PasswordActivity extends BaseActivity {

	private static final String TAG_PASSWORD = "briar.PASSWORD";

	private static final Logger LOG =
			Logger.getLogger(NavDrawerActivity.class.getName());

	@Inject
	protected PasswordController passwordController;

	private Button signInButton;
	private ProgressBar progress;
	private TextInputLayout input;
	private EditText password;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

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
				if (count > 0) AndroidUtils.setError(input, null, false);
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		if (containsContextResultHandler(TAG_PASSWORD)) {
			signInButton.setVisibility(INVISIBLE);
			progress.setVisibility(VISIBLE);
		}

		LOG.info("not tryAgain() " + this.hashCode());
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

		UiContextResultHandler<Boolean> crh =
				new UiContextResultHandler<Boolean>(this, TAG_PASSWORD) {
					@Override
					public void onResultUi(@NonNull Boolean result,
							@NonNull DestroyableContextManager context) {
						PasswordActivity pa =
								((PasswordActivity) getContextManager());
						if (result) {
							pa.setResult(RESULT_OK);
							pa.finish();
						} else {
							pa.tryAgain();
						}
					}

				};
		passwordController.validatePassword(password.getText().toString(), crh);
	}

	private void tryAgain() {
		LOG.info("tryAgain() " + this.hashCode());
		AndroidUtils.setError(input, getString(R.string.try_again), true);
		signInButton.setVisibility(VISIBLE);
		progress.setVisibility(INVISIBLE);
		password.setText("");

		// show the keyboard again
		showSoftKeyboard(password);
	}
}
