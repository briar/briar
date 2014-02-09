package org.briarproject.android;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static android.widget.LinearLayout.VERTICAL;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.util.FixedVerticalSpace;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.util.StringUtils;

import roboguice.activity.RoboActivity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class PasswordActivity extends RoboActivity {

	@Inject private DatabaseConfig databaseConfig;
	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private TextView enterPassword = null;
	private Button continueButton = null;
	private ProgressBar progress = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile CryptoComponent crypto;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		SharedPreferences prefs = getSharedPreferences("db", MODE_PRIVATE);
		String hex = prefs.getString("key", null);
		if(hex == null) throw new IllegalStateException();
		final byte[] encrypted = StringUtils.fromHexString(hex);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		int pad = LayoutUtils.getPadding(this);

		enterPassword = new TextView(this);
		enterPassword.setGravity(CENTER);
		enterPassword.setTextSize(18);
		enterPassword.setPadding(pad, pad, pad, 0);
		enterPassword.setText(R.string.enter_password);
		layout.addView(enterPassword);

		final EditText passwordEntry = new EditText(this);
		passwordEntry.setId(1);
		passwordEntry.setMaxLines(1);
		int inputType = TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD;
		passwordEntry.setInputType(inputType);
		passwordEntry.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int action, KeyEvent e) {
				validatePassword(encrypted, passwordEntry.getText());
				return true;
			}
		});
		layout.addView(passwordEntry);

		// Adjusting the padding of buttons and EditTexts has the wrong results
		FixedVerticalSpace space = new FixedVerticalSpace(this);
		space.setHeight(pad);
		layout.addView(space);

		continueButton = new Button(this);
		continueButton.setLayoutParams(WRAP_WRAP);
		continueButton.setText(R.string.continue_button);
		continueButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				validatePassword(encrypted, passwordEntry.getText());
			}
		});
		layout.addView(continueButton);

		progress = new ProgressBar(this);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		progress.setVisibility(GONE);
		layout.addView(progress);
		setContentView(layout);
	}

	private void validatePassword(final byte[] encrypted, Editable e) {
		if(enterPassword == null || continueButton == null || progress == null)
			return;
		// Hide the soft keyboard
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).toggleSoftInput(HIDE_IMPLICIT_ONLY, 0);
		// Replace the button with a progress bar
		continueButton.setVisibility(GONE);
		progress.setVisibility(VISIBLE);
		// Decrypt the database key in a background thread
		int length = e.length();
		final char[] password = new char[length];
		e.getChars(0, length, password, 0);
		e.delete(0, length);
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				byte[] key = crypto.decryptWithPassword(encrypted, password);
				if(key == null) {
					tryAgain();
				} else {
					databaseConfig.setEncryptionKey(key);
					setResultAndFinish();
				}
			}
		});
	}

	private void tryAgain() {
		runOnUiThread(new Runnable() {
			public void run() {
				enterPassword.setText(R.string.try_again);
				continueButton.setVisibility(VISIBLE);
				progress.setVisibility(GONE);
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
