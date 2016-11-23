package org.briarproject.briar.android.blog;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.view.TextInputView;
import org.briarproject.briar.android.view.TextInputView.TextInputListener;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPost;
import org.briarproject.briar.api.blog.BlogPostFactory;

import java.security.GeneralSecurityException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;

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
	public void onStart() {
		super.onStart();
		notificationManager.blockNotification(groupId);
	}

	@Override
	public void onStop() {
		super.onStop();
		notificationManager.unblockNotification(groupId);
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
					LocalAuthor author = identityManager.getLocalAuthor();
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
