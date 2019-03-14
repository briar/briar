package org.briarproject.briar.android.blog;

import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.api.feed.FeedManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

public class RssFeedImportActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(RssFeedImportActivity.class.getName());

	private EditText urlInput;
	private Button importButton;
	private ProgressBar progressBar;

	@Inject
	@IoExecutor
	Executor ioExecutor;

	@Inject
	@SuppressWarnings("WeakerAccess")
	volatile FeedManager feedManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_rss_feed_import);

		urlInput = findViewById(R.id.urlInput);
		urlInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				enableOrDisableImportButton();
			}
		});
		urlInput.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == IME_ACTION_DONE && importButton.isEnabled() &&
					importButton.getVisibility() == VISIBLE) {
				publish();
				hideSoftKeyboard(urlInput);
				return true;
			}
			return false;
		});

		importButton = findViewById(R.id.importButton);
		importButton.setOnClickListener(v -> publish());

		progressBar = findViewById(R.id.progressBar);
	}

	@Override
	public void onStart() {
		super.onStart();
		if (urlInput.requestFocus()) showSoftKeyboard(urlInput);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void enableOrDisableImportButton() {
		String url = urlInput.getText().toString();
		importButton.setEnabled(validateAndNormaliseUrl(url) != null);
	}

	@Nullable
	private String validateAndNormaliseUrl(String url) {
		if (!Patterns.WEB_URL.matcher(url).matches()) return null;
		try {
			return new URL(url).toString();
		} catch (MalformedURLException e) {
			return null;
		}
	}

	private void publish() {
		// hide import button, show progress bar
		importButton.setVisibility(GONE);
		progressBar.setVisibility(VISIBLE);

		String url = validateAndNormaliseUrl(urlInput.getText().toString());
		if (url == null) throw new AssertionError();
		importFeed(url);
	}

	private void importFeed(String url) {
		ioExecutor.execute(() -> {
			try {
				feedManager.addFeed(url);
				feedImported();
			} catch (DbException | IOException e) {
				logException(LOG, WARNING, e);
				importFailed();
			}
		});
	}

	private void feedImported() {
		runOnUiThreadUnlessDestroyed(this::supportFinishAfterTransition);
	}

	private void importFailed() {
		runOnUiThreadUnlessDestroyed(() -> {
			// hide progress bar, show publish button
			progressBar.setVisibility(GONE);
			importButton.setVisibility(VISIBLE);

			// show error dialog
			AlertDialog.Builder builder =
					new AlertDialog.Builder(RssFeedImportActivity.this,
							R.style.BriarDialogTheme);
			builder.setMessage(R.string.blogs_rss_feeds_import_error);
			builder.setNegativeButton(R.string.cancel, null);
			builder.setPositiveButton(R.string.try_again_button,
					(dialog, which) -> publish());
			AlertDialog dialog = builder.create();
			dialog.show();
		});
	}

}

