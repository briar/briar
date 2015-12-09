package org.briarproject.android;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.system.AndroidFileUtils;
import org.briarproject.util.StringUtils;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
	private Button signInButton;
	private ProgressBar progress;
	private TextView title;
	private EditText password;

	private byte[] encrypted;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile CryptoComponent crypto;
	@Inject private volatile DatabaseConfig databaseConfig;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		String hex = getDbKeyInHex();
		if (hex == null || !databaseConfig.databaseExists()) {
			clearDbPrefs();
			return;
		}
		encrypted = StringUtils.fromHexString(hex);

		setContentView(R.layout.activity_password);
		signInButton = (Button)findViewById(R.id.btn_sign_in);
		progress = (ProgressBar)findViewById(R.id.progress_wheel);
		title = (TextView)findViewById(R.id.title_password);
		password = (EditText)findViewById(R.id.edit_password);
		password.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					validatePassword(encrypted, password.getText());
				}
				return false;
			}
		});
	}

	@Override
	protected void clearDbPrefs() {
		super.clearDbPrefs();
		AndroidFileUtils.deleteFileOrDir(databaseConfig.getDatabaseDirectory());
		gotoAndFinish(SetupActivity.class, RESULT_CANCELED);
	}

	public void onSignInClick(View v) {
		validatePassword(encrypted, password.getText());
	}

	public void onForgottenPasswordClick(View v) {
		// TODO Encapsulate the dialog in a re-usable fragment
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.dialog_title_lost_password);
		builder.setMessage(R.string.dialog_message_lost_password);
		builder.setNegativeButton(R.string.no, null);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				clearDbPrefs();
			}
		});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void validatePassword(final byte[] encrypted, Editable e) {
		hideSoftKeyboard();
		// Replace the button with a progress bar
		signInButton.setVisibility(View.INVISIBLE);
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
				title.setText(R.string.try_again);
				signInButton.setVisibility(VISIBLE);
				progress.setVisibility(GONE);
				password.setText("");
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
