package net.sf.briar.android.identity;

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
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP;
import static net.sf.briar.api.messaging.Rating.GOOD;

import java.io.IOException;
import java.security.KeyPair;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.api.AuthorFactory;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
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

import com.google.inject.Inject;

public class CreateIdentityActivity extends BriarActivity
implements OnEditorActionListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(CreateIdentityActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private EditText nicknameEntry = null;
	private Button createButton = null;
	private ProgressBar progress = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile CryptoComponent crypto;
	@Inject private volatile AuthorFactory authorFactory;
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		TextView chooseNickname = new TextView(this);
		chooseNickname.setGravity(CENTER);
		chooseNickname.setTextSize(18);
		chooseNickname.setPadding(10, 10, 10, 10);
		chooseNickname.setText(R.string.choose_nickname);
		layout.addView(chooseNickname);

		nicknameEntry = new EditText(this) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				if(createButton != null)
					createButton.setEnabled(lengthAfter > 0);
			}
		};
		nicknameEntry.setTextSize(18);
		nicknameEntry.setMaxLines(1);
		nicknameEntry.setPadding(10, 10, 10, 10);
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

		// Bind to the service so we can wait for it to start
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
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
				LocalAuthor a;
				try {
					a = authorFactory.createLocalAuthor(nickname, publicKey,
							privateKey);
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
				storeLocalAuthor(a);
			}
		});
	}

	private void storeLocalAuthor(final LocalAuthor a) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					long now = System.currentTimeMillis();
					db.addLocalAuthor(a);
					db.setRating(a.getId(), GOOD);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Storing author took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
				runOnUiThread(new Runnable() {
					public void run() {
						finish();
					}
				});
			}
		});
	}

	private boolean validateNickname() {
		if(nicknameEntry.getText().toString().equals("")) return false;
		// Hide the soft keyboard
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).toggleSoftInput(HIDE_IMPLICIT_ONLY, 0);
		return true;
	}
}
