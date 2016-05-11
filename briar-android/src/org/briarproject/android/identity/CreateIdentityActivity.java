package org.briarproject.android.identity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.util.StringUtils;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;

public class CreateIdentityActivity extends BriarActivity
		implements OnEditorActionListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(CreateIdentityActivity.class.getName());

	@Inject
	@CryptoExecutor
	protected Executor cryptoExecutor;

	private EditText nicknameEntry;
	private Button createIdentityButton;
	private ProgressBar progress;
	private TextView feedback;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile CryptoComponent crypto;
	@Inject
	protected volatile AuthorFactory authorFactory;
	@Inject
	protected volatile IdentityManager identityManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);
		int pad = LayoutUtils.getPadding(this);
		layout.setPadding(pad, pad, pad, pad);

		TextView chooseNickname = new TextView(this);
		chooseNickname.setGravity(CENTER);
		chooseNickname.setTextSize(18);
		chooseNickname.setText(R.string.choose_nickname);
		layout.addView(chooseNickname);

		nicknameEntry = new EditText(this) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				enableOrDisableCreateButton();
			}
		};
		nicknameEntry.setId(1);
		nicknameEntry.setMaxLines(1);
		int inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_CAP_WORDS;
		nicknameEntry.setInputType(inputType);
		nicknameEntry.setOnEditorActionListener(this);
		layout.addView(nicknameEntry);

		feedback = new TextView(this);
		feedback.setGravity(CENTER);
		feedback.setPadding(0, pad, 0, pad);
		layout.addView(feedback);

		createIdentityButton = new Button(this);
		createIdentityButton.setLayoutParams(WRAP_WRAP);
		createIdentityButton.setText(R.string.create_identity_button);
		createIdentityButton.setEnabled(false);
		createIdentityButton.setOnClickListener(this);
		layout.addView(createIdentityButton);

		progress = new ProgressBar(this);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		progress.setVisibility(GONE);
		layout.addView(progress);

		setContentView(layout);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void enableOrDisableCreateButton() {
		if (progress == null) return; // Not created yet
		createIdentityButton.setEnabled(validateNickname());
	}

	@Override
	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		hideSoftKeyboard(textView);
		return true;
	}

	private boolean validateNickname() {
		String nickname = nicknameEntry.getText().toString();
		int length = StringUtils.toUtf8(nickname).length;
		if (length > MAX_AUTHOR_NAME_LENGTH) {
			feedback.setText(R.string.name_too_long);
			return false;
		}
		feedback.setText("");
		return length > 0;
	}

	@Override
	public void onClick(View view) {
		hideSoftKeyboard(view);
		if (!validateNickname()) return;
		// Replace the button with a progress bar
		createIdentityButton.setVisibility(GONE);
		progress.setVisibility(VISIBLE);
		// Create the identity in a background thread
		final String nickname = nicknameEntry.getText().toString();
		cryptoExecutor.execute(new Runnable() {
			@Override
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

	private void storeLocalAuthor(final LocalAuthor a) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					identityManager.addLocalAuthor(a);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing author took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
				setResultAndFinish(a);
			}
		});
	}

	private void setResultAndFinish(final LocalAuthor a) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Intent i = new Intent();
				i.putExtra("briar.LOCAL_AUTHOR_ID", a.getId().getBytes());
				setResult(RESULT_OK, i);
				Toast.makeText(CreateIdentityActivity.this,
						R.string.identity_created_toast, LENGTH_LONG).show();
				finish();
			}
		});
	}
}
