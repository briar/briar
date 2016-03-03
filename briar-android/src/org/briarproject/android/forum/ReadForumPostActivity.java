package org.briarproject.android.forum;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.AndroidComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.AuthorView;
import org.briarproject.android.util.ElasticHorizontalSpace;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;

public class ReadForumPostActivity extends BriarActivity
implements OnClickListener {

	static final int RESULT_REPLY = RESULT_FIRST_USER;
	static final int RESULT_PREV_NEXT = RESULT_FIRST_USER + 1;

	private static final Logger LOG =
			Logger.getLogger(ReadForumPostActivity.class.getName());

	private GroupId groupId = null;
	private String forumName = null;
	private long minTimestamp = -1;
	private ImageButton prevButton = null, nextButton = null;
	private ImageButton replyButton = null;
	private TextView content = null;
	private int position = -1;

	// Fields that are accessed from background threads must be volatile
	@Inject protected volatile ForumManager forumManager;
	private volatile MessageId messageId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		forumName = i.getStringExtra("briar.FORUM_NAME");
		if (forumName == null) throw new IllegalStateException();
		setTitle(forumName);
		b = i.getByteArrayExtra("briar.MESSAGE_ID");
		if (b == null) throw new IllegalStateException();
		messageId = new MessageId(b);
		String contentType = i.getStringExtra("briar.CONTENT_TYPE");
		if (contentType == null) throw new IllegalStateException();
		long timestamp = i.getLongExtra("briar.TIMESTAMP", -1);
		if (timestamp == -1) throw new IllegalStateException();
		minTimestamp = i.getLongExtra("briar.MIN_TIMESTAMP", -1);
		if (minTimestamp == -1) throw new IllegalStateException();
		position = i.getIntExtra("briar.POSITION", -1);
		if (position == -1) throw new IllegalStateException();
		String authorName = i.getStringExtra("briar.AUTHOR_NAME");
		AuthorId authorId = null;
		b = i.getByteArrayExtra("briar.AUTHOR_ID");
		if (b != null) authorId = new AuthorId(b);
		String s = i.getStringExtra("briar.AUTHOR_STATUS");
		if (s == null) throw new IllegalStateException();
		Author.Status authorStatus = Author.Status.valueOf(s);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);

		ScrollView scrollView = new ScrollView(this);
		scrollView.setLayoutParams(MATCH_WRAP_1);

		LinearLayout message = new LinearLayout(this);
		message.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(MATCH_WRAP);
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		int pad = LayoutUtils.getPadding(this);

		AuthorView authorView = new AuthorView(this);
		authorView.setPadding(0, pad, pad, pad);
		authorView.setLayoutParams(WRAP_WRAP_1);
		authorView.init(authorName, authorId, authorStatus);
		header.addView(authorView);

		TextView date = new TextView(this);
		date.setPadding(pad, pad, pad, pad);
		date.setText(DateUtils.getRelativeTimeSpanString(this, timestamp));
		header.addView(date);
		message.addView(header);

		if (contentType.equals("text/plain")) {
			// Load and display the message body
			content = new TextView(this);
			content.setPadding(pad, 0, pad, pad);
			message.addView(content);
			loadPostBody();
		}
		scrollView.addView(message);
		layout.addView(scrollView);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);
		Resources res = getResources();
		footer.setBackgroundColor(res.getColor(R.color.button_bar_background));

		prevButton = new ImageButton(this);
		prevButton.setBackgroundResource(0);
		prevButton.setImageResource(R.drawable.navigation_previous_item);
		prevButton.setOnClickListener(this);
		footer.addView(prevButton);
		footer.addView(new ElasticHorizontalSpace(this));

		nextButton = new ImageButton(this);
		nextButton.setBackgroundResource(0);
		nextButton.setImageResource(R.drawable.navigation_next_item);
		nextButton.setOnClickListener(this);
		footer.addView(nextButton);
		footer.addView(new ElasticHorizontalSpace(this));

		replyButton = new ImageButton(this);
		replyButton.setBackgroundResource(0);
		replyButton.setImageResource(R.drawable.social_reply_all);
		replyButton.setOnClickListener(this);
		footer.addView(replyButton);
		layout.addView(footer);

		setContentView(layout);
	}

	@Override
	public void injectActivity(AndroidComponent component) {
		component.inject(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (isFinishing()) markPostRead();
	}

	private void markPostRead() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					forumManager.setReadFlag(messageId, true);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Marking read took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void loadPostBody() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					byte[] body = forumManager.getPostBody(messageId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading post took " + duration + " ms");
					displayPostBody(StringUtils.fromUtf8(body));
				} catch (NoSuchMessageException e) {
					finishOnUiThread();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayPostBody(final String body) {
		runOnUiThread(new Runnable() {
			public void run() {
				content.setText(body);
			}
		});
	}

	public void onClick(View view) {
		if (view == prevButton) {
			Intent i = new Intent();
			i.putExtra("briar.POSITION", position - 1);
			setResult(RESULT_PREV_NEXT, i);
			finish();
		} else if (view == nextButton) {
			Intent i = new Intent();
			i.putExtra("briar.POSITION", position + 1);
			setResult(RESULT_PREV_NEXT, i);
			finish();
		} else if (view == replyButton) {
			Intent i = new Intent(this, WriteForumPostActivity.class);
			i.putExtra("briar.GROUP_ID", groupId.getBytes());
			i.putExtra("briar.FORUM_NAME", forumName);
			i.putExtra("briar.PARENT_ID", messageId.getBytes());
			i.putExtra("briar.MIN_TIMESTAMP", minTimestamp);
			startActivity(i);
			setResult(RESULT_REPLY);
			finish();
		}
	}
}
