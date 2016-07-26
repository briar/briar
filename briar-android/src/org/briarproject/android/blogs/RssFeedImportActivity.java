package org.briarproject.android.blogs;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.api.db.DbException;
import org.briarproject.api.feed.FeedManager;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.sync.GroupId;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static java.util.logging.Level.WARNING;

public class RssFeedImportActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(RssFeedImportActivity.class.getName());

	private EditText urlInput;
	private Button importButton;
	private ProgressBar progressBar;

	@Inject
	@IoExecutor
	protected Executor ioExecutor;

	// Fields that are accessed from background threads must be volatile
	private volatile GroupId groupId = null;
	@Inject
	@SuppressWarnings("WeakerAccess")
	volatile FeedManager feedManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// GroupId from Intent
		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No Group in intent.");
		groupId = new GroupId(b);

		setContentView(R.layout.activity_rss_feed_import);

		urlInput = (EditText) findViewById(R.id.urlInput);
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

		importButton = (Button) findViewById(R.id.importButton);
		importButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				publish();
			}
		});

		progressBar = (ProgressBar) findViewById(R.id.progressBar);
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
		if (url.startsWith("http://") || url.startsWith("https://"))
			importButton.setEnabled(true);
		else
			importButton.setEnabled(false);
	}

	private void publish() {
		// hide import button, show progress bar
		importButton.setVisibility(GONE);
		progressBar.setVisibility(VISIBLE);

		importFeed(urlInput.getText().toString());
	}

	private void importFeed(final String url) {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					feedManager.addFeed(url, groupId);
					feedImported();
				} catch (DbException | IOException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					importFailed();
				}
			}
		});
	}

	private void feedImported() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				supportFinishAfterTransition();
			}
		});
	}

	private void importFailed() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// hide progress bar, show publish button
				progressBar.setVisibility(GONE);
				importButton.setVisibility(VISIBLE);

				// show error dialog
				AlertDialog.Builder builder =
						new AlertDialog.Builder(RssFeedImportActivity.this,
								R.style.BriarDialogTheme);
				builder.setMessage(R.string.blogs_rss_feeds_import_error);
				builder.setNegativeButton(R.string.cancel_button, null);
				builder.setPositiveButton(R.string.try_again_button,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								publish();
							}
						});
				AlertDialog dialog = builder.create();
				dialog.show();
			}
		});
	}

}

