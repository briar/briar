package org.briarproject.android.contact;

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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.CommonLayoutParams;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.AuthorId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageFactory;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.util.StringUtils;

import android.content.Intent;
import android.content.res.Resources;
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

public class WritePrivateMessageActivity extends BriarActivity
implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(WritePrivateMessageActivity.class.getName());

	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private TextView from = null;
	private ImageButton sendButton = null;
	private EditText content = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject private volatile MessageFactory messageFactory;
	private volatile String contactName = null;
	private volatile GroupId groupId = null;
	private volatile AuthorId localAuthorId = null;
	private volatile MessageId parentId = null;
	private volatile long timestamp = -1;
	private volatile LocalAuthor localAuthor = null;
	private volatile Group group = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		contactName = i.getStringExtra("briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();
		setTitle(contactName);
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if(b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		b = i.getByteArrayExtra("briar.LOCAL_AUTHOR_ID");
		if(b == null) throw new IllegalStateException();
		localAuthorId = new AuthorId(b);
		b = i.getByteArrayExtra("briar.PARENT_ID");
		if(b != null) parentId = new MessageId(b);
		timestamp = i.getLongExtra("briar.TIMESTAMP", -1);

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
		sendButton.setEnabled(false); // Enabled after loading the group
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
		if(localAuthor == null || group == null) loadAuthorAndGroup();
	}

	private void loadAuthorAndGroup() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					localAuthor = db.getLocalAuthor(localAuthorId);
					group = db.getGroup(groupId);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayLocalAuthor();
				} catch(NoSuchContactException e) {
					finishOnUiThread();
				} catch(NoSuchSubscriptionException e) {
					finishOnUiThread();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayLocalAuthor() {
		runOnUiThread(new Runnable() {
			public void run() {
				Resources res = getResources();
				String format = res.getString(R.string.format_from);
				String name = localAuthor.getName();
				from.setText(String.format(format, name));
				sendButton.setEnabled(true);
			}
		});
	}

	public void onClick(View view) {
		String message = content.getText().toString();
		if(message.equals("")) return;
		createMessage(StringUtils.toUtf8(message));
		Toast.makeText(this, R.string.message_sent_toast, LENGTH_LONG).show();
		finish();
	}

	private void createMessage(final byte[] body) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				// Don't use an earlier timestamp than the parent
				long time = System.currentTimeMillis();
				time = Math.max(time, timestamp + 1);
				try {
					Message m = messageFactory.createAnonymousMessage(parentId,
							group, "text/plain", time, body);
					storeMessage(m);
				} catch(GeneralSecurityException e) {
					throw new RuntimeException(e);
				} catch(IOException e) {
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
					db.addLocalMessage(m);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}
