package org.briarproject.android.blogs;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.api.AndroidNotificationManager;
import org.briarproject.android.view.TextInputView;
import org.briarproject.android.view.TextInputView.TextInputListener;
import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPost;
import org.briarproject.api.blogs.BlogPostFactory;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.util.StringUtils;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;

public class WriteBlogPostActivity extends BriarActivity
		implements OnEditorActionListener, TextInputListener {

	private static final Logger LOG =
			Logger.getLogger(WriteBlogPostActivity.class.getName());

	@Inject
	AndroidNotificationManager notificationManager;

	private TextInputView input;
	private ProgressBar progressBar;

	// Fields that are accessed from background threads must be volatile
	private volatile GroupId groupId;
	@Inject
	volatile IdentityManager identityManager;
	@Inject
	volatile BlogPostFactory blogPostFactory;
	@Inject
	volatile BlogManager blogManager;

	@SuppressWarnings("ConstantConditions")
	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No Group in intent.");
		groupId = new GroupId(b);

		setContentView(R.layout.activity_write_blog_post);

		input = (TextInputView) findViewById(R.id.bodyInput);
		input.setSendButtonEnabled(false);
		input.addTextChangedListener(new TextWatcher() {
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
				enableOrDisablePublishButton();
			}
		});
		input.setListener(this);

		progressBar = (ProgressBar) findViewById(R.id.progressBar);
	}

	@Override
	public void onPause() {
		super.onPause();
		notificationManager.unblockNotification(groupId);
	}

	@Override
	public void onResume() {
		super.onResume();
		notificationManager.blockNotification(groupId);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		input.requestFocus();
		return true;
	}

	private void enableOrDisablePublishButton() {
		input.setSendButtonEnabled(input.getText().length() > 0);
	}

	@Override
	public void onSendClick(String body) {
		// hide publish button, show progress bar
		input.setVisibility(GONE);
		progressBar.setVisibility(VISIBLE);

		body = StringUtils.truncateUtf8(body, MAX_BLOG_POST_BODY_LENGTH);
		storePost(body);
	}

	private void storePost(final String body) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				long now = System.currentTimeMillis();
				try {
					Collection<LocalAuthor> authors =
							identityManager.getLocalAuthors();
					LocalAuthor author = authors.iterator().next();
					BlogPost p = blogPostFactory
							.createBlogPost(groupId, now, null, author, body);
					blogManager.addLocalPost(p);
					postPublished();
				} catch (DbException | GeneralSecurityException | FormatException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					postFailedToPublish();
				}
			}
		});
	}

	private void postPublished() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				setResult(RESULT_OK);
				supportFinishAfterTransition();
			}
		});
	}

	private void postFailedToPublish() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				// hide progress bar, show publish button
				progressBar.setVisibility(GONE);
				input.setVisibility(VISIBLE);
				// TODO show error
			}
		});
	}
}
