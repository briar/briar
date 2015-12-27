package org.briarproject.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.briarproject.R;
import org.briarproject.android.util.StrengthMeter;
import org.briarproject.api.android.ReferenceManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.PasswordStrengthEstimator;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.util.StringUtils;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import roboguice.inject.InjectView;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static java.util.logging.Level.INFO;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;

public class SetupActivity extends BaseActivity implements OnClickListener,
OnEditorActionListener {

	private static final Logger LOG =
			Logger.getLogger(SetupActivity.class.getName());

	@Inject @CryptoExecutor private Executor cryptoExecutor;
	@Inject private PasswordStrengthEstimator strengthEstimator;
	@InjectView(R.id.nickname_entry) EditText nicknameEntry;
	@InjectView(R.id.password_entry) EditText passwordEntry;
	@InjectView(R.id.password_confirm) EditText passwordConfirmation;
	@InjectView(R.id.strength_meter) StrengthMeter strengthMeter;
	@InjectView(R.id.create_account) Button createAccountButton;
	@InjectView(R.id.progress_wheel) ProgressBar progress;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile CryptoComponent crypto;
	@Inject private volatile DatabaseConfig databaseConfig;
	@Inject private volatile AuthorFactory authorFactory;
	@Inject private volatile ReferenceManager referenceManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_setup);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		TextWatcher tw = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
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
		float strength = strengthEstimator.estimateStrength(firstPassword);
		strengthMeter.setStrength(strength);
		if (nicknameLength > MAX_AUTHOR_NAME_LENGTH)
			nicknameEntry.setError(getString(R.string.name_too_long));
		if (firstPassword.length() > 0 && strength < WEAK)
			passwordEntry.setError(getString(R.string.password_too_weak));
		if (secondPassword.length() > 0 && !passwordsMatch)
			passwordConfirmation.setError(getString(R.string.passwords_do_not_match));
		createAccountButton.setEnabled(nicknameLength > 0
				&& nicknameLength <= MAX_AUTHOR_NAME_LENGTH
				&& passwordsMatch && strength >= WEAK);
	}

	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		// Hide the soft keyboard
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).toggleSoftInput(HIDE_IMPLICIT_ONLY, 0);
		return true;
	}

	public void onClick(View view) {
		// Replace the feedback text and button with a progress bar
		createAccountButton.setVisibility(GONE);
		progress.setVisibility(VISIBLE);
		final String nickname = nicknameEntry.getText().toString();
		final String password = passwordEntry.getText().toString();
		// Store the DB key and create the identity in a background thread
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				SecretKey key = crypto.generateSecretKey();
				databaseConfig.setEncryptionKey(key);
				byte[] encrypted = encryptDatabaseKey(key, password);
				storeEncryptedDatabaseKey(encrypted);
				LocalAuthor localAuthor = createLocalAuthor(nickname);
				showDashboard(referenceManager.putReference(localAuthor,
						LocalAuthor.class));
			}
		});
	}

	private void storeEncryptedDatabaseKey(final byte[] encrypted) {
		long now = System.currentTimeMillis();
		SharedPreferences prefs = getSharedPreferences("db", MODE_PRIVATE);
		Editor editor = prefs.edit();
		editor.putString("key", StringUtils.toHexString(encrypted));
		editor.commit();
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Key storage took " + duration + " ms");
	}

	private byte[] encryptDatabaseKey(SecretKey key, String password) {
		long now = System.currentTimeMillis();
		byte[] encrypted = crypto.encryptWithPassword(key.getBytes(), password);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Key derivation took " + duration + " ms");
		return encrypted;
	}

	private LocalAuthor createLocalAuthor(String nickname) {
		long now = System.currentTimeMillis();
		KeyPair keyPair = crypto.generateSignatureKeyPair();
		byte[] publicKey = keyPair.getPublic().getEncoded();
		byte[] privateKey = keyPair.getPrivate().getEncoded();
		LocalAuthor localAuthor = authorFactory.createLocalAuthor(nickname,
				publicKey, privateKey);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Identity creation took " + duration + " ms");
		return localAuthor;
	}

	private void showDashboard(final long handle) {
		runOnUiThread(new Runnable() {
			public void run() {
				Intent i = new Intent(SetupActivity.this,
						DashboardActivity.class);
				i.putExtra("briar.LOCAL_AUTHOR_HANDLE", handle);
				i.setFlags(FLAG_ACTIVITY_NEW_TASK);
				startActivity(i);
				finish();
			}
		});
	}
}
