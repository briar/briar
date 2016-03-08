package org.briarproject.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
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
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.util.StringUtils;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class PasswordActivity extends BaseActivity {

	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private Button signInButton;
	private ProgressBar progress;
	private TextInputLayout input;
	private EditText password;

	private byte[] encrypted;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile CryptoComponent crypto;
	@Inject private volatile DatabaseConfig databaseConfig;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		String hex = getEncryptedDatabaseKey();
		if (hex == null || !databaseConfig.databaseExists()) {
			clearSharedPrefsAndDeleteEverything();
			return;
		}
		encrypted = StringUtils.fromHexString(hex);

		setContentView(R.layout.activity_password);
		signInButton = (Button) findViewById(R.id.btn_sign_in);
		progress = (ProgressBar) findViewById(R.id.progress_wheel);
		input = (TextInputLayout) findViewById(R.id.password_layout);
		password = (EditText) findViewById(R.id.edit_password);
		password.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				hideSoftKeyboard(password);
				validatePassword(encrypted, password.getText());
				return true;
			}
		});
		password.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				if (count > 0) AndroidUtils.setError(input, null, false);
			}

			@Override
			public void afterTextChanged(Editable s) {}
		});
	}

	@Override
	public void onBackPressed() {
		// Show the home screen rather than another password prompt
		Intent intent = new Intent(ACTION_MAIN);
		intent.addCategory(CATEGORY_HOME);
		startActivity(intent);
	}

	private void clearSharedPrefsAndDeleteEverything() {
		clearSharedPrefs();
		AndroidUtils.deleteAppData(this);
		setResult(RESULT_CANCELED);
		startActivity(new Intent(this, SetupActivity.class));
		finish();
	}

	public void onSignInClick(View v) {
		validatePassword(encrypted, password.getText());
	}

	public void onForgottenPasswordClick(View v) {
		// TODO Encapsulate the dialog in a re-usable fragment
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

	private void validatePassword(final byte[] encrypted, Editable e) {
		hideSoftKeyboard(password);
		// Replace the button with a progress bar
		signInButton.setVisibility(INVISIBLE);
		progress.setVisibility(VISIBLE);
		// Decrypt the database key in a background thread
		final String password = e.toString();
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				byte[] key = crypto.decryptWithPassword(encrypted, password);
				if (key == null) {
					tryAgain();
				} else {
					databaseConfig.setEncryptionKey(new SecretKey(key));
					setResultAndFinish();
				}
			}
		});
	}

	private void tryAgain() {
		runOnUiThread(new Runnable() {
			public void run() {
				AndroidUtils.setError(input, getString(R.string.try_again),
						true);
				signInButton.setVisibility(VISIBLE);
				progress.setVisibility(INVISIBLE);
				password.setText("");

				// show the keyboard again
				showSoftKeyboard(password);
			}
		});
	}

	private void setResultAndFinish() {
		runOnUiThread(new Runnable() {
			public void run() {
				setResult(RESULT_OK);
				finish();
			}
		});
	}
}
