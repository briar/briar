package org.briarproject.android.forum;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.identity.LocalAuthorItem;
import org.briarproject.android.identity.LocalAuthorItemComparator;
import org.briarproject.android.identity.LocalAuthorSpinnerAdapter;
import org.briarproject.android.util.CommonLayoutParams;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.RelativeLayout.ALIGN_PARENT_LEFT;
import static android.widget.RelativeLayout.ALIGN_PARENT_RIGHT;
import static android.widget.RelativeLayout.CENTER_VERTICAL;
import static android.widget.RelativeLayout.LEFT_OF;
import static android.widget.RelativeLayout.RIGHT_OF;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;

public class WriteForumPostActivity extends BriarActivity
implements OnItemSelectedListener, OnClickListener {

	private static final int REQUEST_CREATE_IDENTITY = 2;
	private static final Logger LOG =
			Logger.getLogger(WriteForumPostActivity.class.getName());

	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private LocalAuthorSpinnerAdapter adapter = null;
	private Spinner spinner = null;
	private ImageButton sendButton = null;
	private EditText content = null;
	private AuthorId localAuthorId = null;
	private GroupId groupId = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile IdentityManager identityManager;
	@Inject private volatile ForumManager forumManager;
	@Inject private volatile ForumPostFactory forumPostFactory;
	@Inject private volatile CryptoComponent crypto;
	private volatile MessageId parentId = null;
	private volatile long minTimestamp = -1;
	private volatile LocalAuthor localAuthor = null;
	private volatile Forum forum = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		String forumName = i.getStringExtra("briar.FORUM_NAME");
		if (forumName == null) throw new IllegalStateException();
		setTitle(forumName);
		minTimestamp = i.getLongExtra("briar.MIN_TIMESTAMP", -1);
		if (minTimestamp == -1) throw new IllegalStateException();
		b = i.getByteArrayExtra("briar.PARENT_ID");
		if (b != null) parentId = new MessageId(b);

		if (state != null) {
			b = state.getByteArray("briar.LOCAL_AUTHOR_ID");
			if (b != null) localAuthorId = new AuthorId(b);
		}

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_WRAP);
		layout.setOrientation(VERTICAL);
		int pad = LayoutUtils.getPadding(this);
		layout.setPadding(pad, 0, pad, pad);

		RelativeLayout header = new RelativeLayout(this);

		TextView from = new TextView(this);
		from.setId(1);
		from.setTextSize(18);
		from.setText(R.string.from);
		RelativeLayout.LayoutParams left = CommonLayoutParams.relative();
		left.addRule(ALIGN_PARENT_LEFT);
		left.addRule(CENTER_VERTICAL);
		header.addView(from, left);

		adapter = new LocalAuthorSpinnerAdapter(this, true);
		spinner = new Spinner(this);
		spinner.setId(2);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		RelativeLayout.LayoutParams between = CommonLayoutParams.relative();
		between.addRule(CENTER_VERTICAL);
		between.addRule(RIGHT_OF, 1);
		between.addRule(LEFT_OF, 3);
		header.addView(spinner, between);

		sendButton = new ImageButton(this);
		sendButton.setId(3);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false); // Enabled after loading the forum
		sendButton.setOnClickListener(this);
		RelativeLayout.LayoutParams right = CommonLayoutParams.relative();
		right.addRule(ALIGN_PARENT_RIGHT);
		right.addRule(CENTER_VERTICAL);
		header.addView(sendButton, right);
		layout.addView(header);

		content = new EditText(this);
		content.setId(4);
		int inputType = TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| TYPE_TEXT_FLAG_CAP_SENTENCES;
		content.setInputType(inputType);
		content.setHint(R.string.forum_post_hint);
		layout.addView(content);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		loadAuthorsAndForum();
	}

	private void loadAuthorsAndForum() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<LocalAuthor> localAuthors =
							identityManager.getLocalAuthors();
					forum = forumManager.getForum(groupId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayAuthorsAndForum(localAuthors);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayAuthorsAndForum(
			final Collection<LocalAuthor> localAuthors) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (localAuthors.isEmpty()) throw new IllegalStateException();
				adapter.clear();
				for (LocalAuthor a : localAuthors)
					adapter.add(new LocalAuthorItem(a));
				adapter.sort(LocalAuthorItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
				int count = adapter.getCount();
				for (int i = 0; i < count; i++) {
					LocalAuthorItem item = adapter.getItem(i);
					if (item == LocalAuthorItem.ANONYMOUS) continue;
					if (item == LocalAuthorItem.NEW) continue;
					if (item.getLocalAuthor().getId().equals(localAuthorId)) {
						localAuthor = item.getLocalAuthor();
						spinner.setSelection(i);
						break;
					}
				}
				setTitle(forum.getName());
				sendButton.setEnabled(true);
			}
		});
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		if (localAuthorId != null) {
			byte[] b =  localAuthorId.getBytes();
			state.putByteArray("briar.LOCAL_AUTHOR_ID", b);
		}
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_CREATE_IDENTITY && result == RESULT_OK) {
			byte[] b = data.getByteArrayExtra("briar.LOCAL_AUTHOR_ID");
			if (b == null) throw new IllegalStateException();
			localAuthorId = new AuthorId(b);
			loadAuthorsAndForum();
		}
	}

	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		LocalAuthorItem item = adapter.getItem(position);
		if (item == LocalAuthorItem.ANONYMOUS) {
			localAuthor = null;
			localAuthorId = null;
		} else if (item == LocalAuthorItem.NEW) {
			localAuthor = null;
			localAuthorId = null;
			Intent i = new Intent(this, CreateIdentityActivity.class);
			startActivityForResult(i, REQUEST_CREATE_IDENTITY);
		} else {
			localAuthor = item.getLocalAuthor();
			localAuthorId = localAuthor.getId();
		}
	}

	public void onNothingSelected(AdapterView<?> parent) {
		localAuthor = null;
		localAuthorId = null;
	}

	public void onClick(View view) {
		if (forum == null) throw new IllegalStateException();
		String body = content.getText().toString();
		if (body.equals("")) return;
		createPost(StringUtils.toUtf8(body));
		Toast.makeText(this, R.string.post_sent_toast, LENGTH_LONG).show();
		finish();
	}

	private void createPost(final byte[] body) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				// Don't use an earlier timestamp than the newest post
				long timestamp = System.currentTimeMillis();
				timestamp = Math.max(timestamp, minTimestamp);
				Message m;
				try {
					if (localAuthor == null) {
						m = forumPostFactory.createAnonymousPost(parentId,
								forum, "text/plain", timestamp, body);
					} else {
						KeyParser keyParser = crypto.getSignatureKeyParser();
						byte[] b = localAuthor.getPrivateKey();
						PrivateKey authorKey = keyParser.parsePrivateKey(b);
						m = forumPostFactory.createPseudonymousPost(parentId,
								forum, localAuthor, authorKey, "text/plain",
								timestamp, body);
					}
				} catch (GeneralSecurityException e) {
					throw new RuntimeException(e);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				storePost(m);
			}
		});
	}

	private void storePost(final Message m) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					forumManager.addLocalPost(m);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}
