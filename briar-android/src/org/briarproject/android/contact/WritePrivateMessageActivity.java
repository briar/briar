package org.briarproject.android.contact;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.CommonLayoutParams;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateConversation;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.text.TextUtils.TruncateAt.END;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.RelativeLayout.ALIGN_PARENT_LEFT;
import static android.widget.RelativeLayout.ALIGN_PARENT_RIGHT;
import static android.widget.RelativeLayout.CENTER_VERTICAL;
import static android.widget.RelativeLayout.LEFT_OF;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;

public class WritePrivateMessageActivity extends BriarActivity
implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(WritePrivateMessageActivity.class.getName());

	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private TextView from = null;
	private ImageButton sendButton = null;
	private EditText content = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile IdentityManager identityManager;
	@Inject private volatile MessagingManager messagingManager;
	@Inject private volatile PrivateMessageFactory privateMessageFactory;
	private volatile GroupId groupId = null;
	private volatile AuthorId localAuthorId = null;
	private volatile MessageId parentId = null;
	private volatile long minTimestamp = -1;
	private volatile LocalAuthor localAuthor = null;
	private volatile PrivateConversation conversation = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		String contactName = i.getStringExtra("briar.CONTACT_NAME");
		if (contactName == null) throw new IllegalStateException();
		setTitle(contactName);
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		b = i.getByteArrayExtra("briar.LOCAL_AUTHOR_ID");
		if (b == null) throw new IllegalStateException();
		minTimestamp = i.getLongExtra("briar.MIN_TIMESTAMP", -1);
		if (minTimestamp == -1) throw new IllegalStateException();
		localAuthorId = new AuthorId(b);
		b = i.getByteArrayExtra("briar.PARENT_ID");
		if (b != null) parentId = new MessageId(b);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_WRAP);
		layout.setOrientation(VERTICAL);
		int pad = LayoutUtils.getPadding(this);
		layout.setPadding(pad, 0, pad, pad);

		RelativeLayout header = new RelativeLayout(this);

		from = new TextView(this);
		from.setId(1);
		from.setTextSize(18);
		from.setSingleLine();
		from.setEllipsize(END);
		from.setPadding(0, 0, pad, 0);
		from.setText(R.string.from);
		RelativeLayout.LayoutParams leftOf = CommonLayoutParams.relative();
		leftOf.addRule(ALIGN_PARENT_LEFT);
		leftOf.addRule(CENTER_VERTICAL);
		leftOf.addRule(LEFT_OF, 2);
		header.addView(from, leftOf);

		sendButton = new ImageButton(this);
		sendButton.setId(2);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false); // Enabled after loading the conversation
		sendButton.setOnClickListener(this);
		RelativeLayout.LayoutParams right = CommonLayoutParams.relative();
		right.addRule(ALIGN_PARENT_RIGHT);
		right.addRule(CENTER_VERTICAL);
		header.addView(sendButton, right);
		layout.addView(header);

		content = new EditText(this);
		content.setId(3);
		int inputType = TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| TYPE_TEXT_FLAG_CAP_SENTENCES;
		content.setInputType(inputType);
		content.setHint(R.string.private_message_hint);
		layout.addView(content);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (localAuthor == null || conversation == null)
			loadAuthorAndConversation();
	}

	private void loadAuthorAndConversation() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					localAuthor = identityManager.getLocalAuthor(localAuthorId);
					conversation = messagingManager.getConversation(groupId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayLocalAuthor();
				} catch (NoSuchContactException e) {
					finishOnUiThread();
				} catch (NoSuchSubscriptionException e) {
					finishOnUiThread();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayLocalAuthor() {
		runOnUiThread(new Runnable() {
			public void run() {
				String format = getString(R.string.format_from);
				String name = localAuthor.getName();
				from.setText(String.format(format, name));
				sendButton.setEnabled(true);
			}
		});
	}

	public void onClick(View view) {
		String message = content.getText().toString();
		if (message.equals("")) return;
		createMessage(StringUtils.toUtf8(message));
		Toast.makeText(this, R.string.message_sent_toast, LENGTH_LONG).show();
		finish();
	}

	private void createMessage(final byte[] body) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				// Don't use an earlier timestamp than the newest message
				long timestamp = System.currentTimeMillis();
				timestamp = Math.max(timestamp, minTimestamp);
				try {
					Message m = privateMessageFactory.createPrivateMessage(
							parentId, conversation, "text/plain", timestamp,
							body);
					storeMessage(m);
				} catch (GeneralSecurityException e) {
					throw new RuntimeException(e);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	private void storeMessage(final Message m) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					messagingManager.addLocalMessage(m);
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
