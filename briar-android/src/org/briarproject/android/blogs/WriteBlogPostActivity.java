package org.briarproject.android.blogs;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
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
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_TITLE_LENGTH;

public class WriteBlogPostActivity extends BriarActivity
		implements OnEditorActionListener {

	private static final Logger LOG =
			Logger.getLogger(WriteBlogPostActivity.class.getName());
	private static final String contentType = "text/plain";

	private TextInputEditText titleInput;
	private EditText bodyInput;
	private Button publishButton;
	private ProgressBar progressBar;

	// Fields that are accessed from background threads must be volatile
	private volatile GroupId groupId;
	@Inject
	protected volatile IdentityManager identityManager;
	@Inject
	volatile BlogPostFactory blogPostFactory;
	@Inject
	volatile BlogManager blogManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No Group in intent.");
		groupId = new GroupId(b);
//		String blogName = i.getStringExtra(BLOG_NAME);
//		if (blogName != null) setTitle(blogName);

		setContentView(R.layout.activity_write_blog_post);
//		String title =
//				getTitle() + ": " + getString(R.string.blogs_write_blog_post);
//		setTitle(title);

		TextInputLayout titleLayout =
				(TextInputLayout) findViewById(R.id.titleLayout);
		if (titleLayout != null) {
			titleLayout.setCounterMaxLength(MAX_BLOG_POST_TITLE_LENGTH);
		}
		titleInput = (TextInputEditText) findViewById(R.id.titleInput);
		if (titleInput != null) {
			titleInput.setOnEditorActionListener(this);
		}

		bodyInput = (EditText) findViewById(R.id.bodyInput);
		bodyInput.addTextChangedListener(new TextWatcher() {
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
				showOrHidePublishButton();
			}
		});

		publishButton = (Button) findViewById(R.id.publishButton);
		publishButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				publish();
			}
		});

		progressBar = (ProgressBar) findViewById(R.id.progressBar);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		bodyInput.requestFocus();
		return true;
	}

	private void showOrHidePublishButton() {
		int bodyLength =
				StringUtils.toUtf8(bodyInput.getText().toString()).length;
		if (bodyLength > 0 && bodyLength <= MAX_BLOG_POST_BODY_LENGTH &&
				titleInput.getText().length() <= MAX_BLOG_POST_TITLE_LENGTH)
			publishButton.setEnabled(true);
		else
			publishButton.setEnabled(false);
	}

	private void publish() {
		// title
		String title = titleInput.getText().toString();
		if (title.length() > MAX_BLOG_POST_TITLE_LENGTH) return;
		if (title.length() == 0) title = null;

		// body
		byte[] body = StringUtils.toUtf8(bodyInput.getText().toString());

		// hide publish button, show progress bar
		publishButton.setVisibility(GONE);
		progressBar.setVisibility(VISIBLE);

		storePost(title, body);
	}

	private void storePost(final String title, final byte[] body) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				long now = System.currentTimeMillis();
				try {
					Collection<LocalAuthor> authors =
							identityManager.getLocalAuthors();
					LocalAuthor author = authors.iterator().next();
					BlogPost p = blogPostFactory
							.createBlogPost(groupId, title, now, null, author,
									contentType, body);
					blogManager.addLocalPost(p);
					postPublished();
				} catch (DbException | GeneralSecurityException | FormatException e) {
					// TODO show error
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void postPublished() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				setResult(RESULT_OK);
				supportFinishAfterTransition();
			}
		});
	}

}
