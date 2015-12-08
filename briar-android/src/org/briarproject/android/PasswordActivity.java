package org.briarproject.android;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import java.io.File;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.util.StringUtils;

import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class PasswordActivity extends BaseActivity {

	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private Button mSignInButton;
	private ProgressBar mProgress;
	private TextView mTitle;
	private EditText mPassword;

	private byte[] encrypted;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile CryptoComponent crypto;
	@Inject private volatile DatabaseConfig databaseConfig;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		String hex = this.getDbKeyInHex();
		if (hex == null || !databaseConfig.databaseExists()) {
			this.clearDbPrefs();
			return;
		}
		this.encrypted = StringUtils.fromHexString(hex);

		this.setContentView(R.layout.activity_password);
		this.mSignInButton = (Button)findViewById(R.id.btn_sign_in);
		this.mProgress = (ProgressBar)findViewById(R.id.progress_wheel);
		this.mTitle = (TextView)findViewById(R.id.title_password);
		this.mPassword = (EditText)findViewById(R.id.edit_password);
		this.mPassword.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					validatePassword(encrypted, PasswordActivity.this.mPassword.getText());
				}
				return false;
			}
		});
	}

	@Override
	protected void clearDbPrefs() {
		super.clearDbPrefs();
		this.delete(databaseConfig.getDatabaseDirectory());
		this.gotoAndFinish(SetupActivity.class, RESULT_CANCELED);
	}

	public void onSignInClick(View v) {
		this.validatePassword(this.encrypted, this.mPassword.getText());
	}

	public void onForgottenPasswordClick(View v) {
		this.clearDbPrefs();
	}

	private void delete(File f) {
		if (f.isFile()) f.delete();
		else if (f.isDirectory()) for (File child : f.listFiles()) delete(child);
	}

	private void validatePassword(final byte[] encrypted, Editable e) {
		this.hideSoftKeyboard();
		// Replace the button with a progress bar
		this.mSignInButton.setVisibility(View.INVISIBLE);
		this.mProgress.setVisibility(VISIBLE);
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
				PasswordActivity.this.mTitle.setText(R.string.try_again);
				PasswordActivity.this.mSignInButton.setVisibility(VISIBLE);
				PasswordActivity.this.mProgress.setVisibility(GONE);
				PasswordActivity.this.mPassword.setText("");
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
