package org.briarproject.android.identity;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;
import static org.briarproject.api.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.AuthorFactory;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.android.DatabaseUiExecutor;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.util.StringUtils;

import android.content.Intent;
import android.os.Bundle;
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

public class CreateIdentityActivity extends BriarActivity
implements OnEditorActionListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(CreateIdentityActivity.class.getName());

	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private EditText nicknameEntry = null;
	private Button createButton = null;
	private ProgressBar progress = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile CryptoComponent crypto;
	@Inject private volatile AuthorFactory authorFactory;
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		int pad = LayoutUtils.getPadding(this);

		TextView chooseNickname = new TextView(this);
		chooseNickname.setGravity(CENTER);
		chooseNickname.setTextSize(18);
		chooseNickname.setPadding(pad, pad, pad, 0);
		chooseNickname.setText(R.string.choose_nickname);
		layout.addView(chooseNickname);

		nicknameEntry = new EditText(this) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				if(createButton != null)
					createButton.setEnabled(getText().length() > 0);
			}
		};
		nicknameEntry.setId(1);
		nicknameEntry.setMaxLines(1);
		int inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_CAP_WORDS;
		nicknameEntry.setInputType(inputType);
		nicknameEntry.setOnEditorActionListener(this);
		layout.addView(nicknameEntry);

		createButton = new Button(this);
		createButton.setLayoutParams(WRAP_WRAP);
		createButton.setText(R.string.create_button);
		createButton.setEnabled(false);
		createButton.setOnClickListener(this);
		layout.addView(createButton);

		progress = new ProgressBar(this);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		progress.setVisibility(GONE);
		layout.addView(progress);

		setContentView(layout);
	}

	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		validateNickname();
		return true;
	}

	public void onClick(View view) {
		if(!validateNickname()) return;
		final String nickname = nicknameEntry.getText().toString();
		// Replace the button with a progress bar
		createButton.setVisibility(GONE);
		progress.setVisibility(VISIBLE);
		// Create the identity in a background thread
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				KeyPair keyPair = crypto.generateSignatureKeyPair();
				final byte[] publicKey = keyPair.getPublic().getEncoded();
				final byte[] privateKey = keyPair.getPrivate().getEncoded();
				LocalAuthor a = authorFactory.createLocalAuthor(nickname,
						publicKey, privateKey);
				storeLocalAuthor(a);
			}
		});
	}

	private boolean validateNickname() {
		if(nicknameEntry.getText().length() == 0) return false;
		byte[] b = StringUtils.toUtf8(nicknameEntry.getText().toString());
		if(b.length > MAX_AUTHOR_NAME_LENGTH) return false;
		// Hide the soft keyboard
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).toggleSoftInput(HIDE_IMPLICIT_ONLY, 0);
		return true;
	}

	private void storeLocalAuthor(final LocalAuthor a) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					db.addLocalAuthor(a);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Storing author took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
				setResultAndFinish(a);
			}
		});
	}

	private void setResultAndFinish(final LocalAuthor a) {
		runOnUiThread(new Runnable() {
			public void run() {
				Intent i = new Intent();
				i.putExtra("briar.LOCAL_AUTHOR_ID", a.getId().getBytes());
				setResult(RESULT_OK, i);
				finish();
			}
		});
	}
}
