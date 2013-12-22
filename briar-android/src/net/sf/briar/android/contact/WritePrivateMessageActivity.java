package net.sf.briar.android.contact;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_WRAP;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import net.sf.briar.R;
import net.sf.briar.android.util.HorizontalSpace;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.NoSuchSubscriptionException;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import net.sf.briar.api.messaging.MessageId;
import roboguice.activity.RoboActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WritePrivateMessageActivity extends RoboActivity
implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(WritePrivateMessageActivity.class.getName());

	private TextView from = null, to = null;
	private ImageButton sendButton = null;
	private EditText content = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;
	@Inject @CryptoExecutor private volatile Executor cryptoExecutor;
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
		contactName = i.getStringExtra("net.sf.briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();
		byte[] b = i.getByteArrayExtra("net.sf.briar.GROUP_ID");
		if(b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		b = i.getByteArrayExtra("net.sf.briar.LOCAL_AUTHOR_ID");
		if(b == null) throw new IllegalStateException();
		localAuthorId = new AuthorId(b);
		b = i.getByteArrayExtra("net.sf.briar.PARENT_ID");
		if(b != null) parentId = new MessageId(b);
		timestamp = i.getLongExtra("net.sf.briar.TIMESTAMP", -1);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_WRAP);
		layout.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(MATCH_WRAP);
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		from = new TextView(this);
		from.setTextSize(18);
		from.setPadding(10, 10, 10, 10);
		from.setText(R.string.from);
		header.addView(from);

		header.addView(new HorizontalSpace(this));

		sendButton = new ImageButton(this);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false); // Enabled after loading the group
		sendButton.setOnClickListener(this);
		header.addView(sendButton);
		layout.addView(header);

		to = new TextView(this);
		to.setTextSize(18);
		to.setPadding(10, 0, 10, 10);
		String format = getResources().getString(R.string.format_to);
		to.setText(String.format(format, contactName));
		layout.addView(to);

		content = new EditText(this);
		content.setId(1);
		int inputType = TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| TYPE_TEXT_FLAG_CAP_SENTENCES;
		content.setInputType(inputType);
		layout.addView(content);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		loadAuthorAndGroup();
	}

	private void loadAuthorAndGroup() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					localAuthor = db.getLocalAuthor(localAuthorId);
					group = db.getGroup(groupId);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayLocalAuthor();
				} catch(NoSuchContactException e) {
					finish();
				} catch(NoSuchSubscriptionException e) {
					finish();
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
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
		if(localAuthor == null) throw new IllegalStateException();
		try {
			createMessage(content.getText().toString().getBytes("UTF-8"));
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		Toast.makeText(this, R.string.message_sent_toast, LENGTH_LONG).show();
		finish();
	}

	private void createMessage(final byte[] body) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				// Don't use an earlier timestamp than the parent
				long time = System.currentTimeMillis();
				time = Math.max(time, timestamp + 1);
				Message m;
				try {
					m = messageFactory.createAnonymousMessage(parentId, group,
							"text/plain", time, body);
				} catch(GeneralSecurityException e) {
					throw new RuntimeException(e);
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
				storeMessage(m);
			}
		});
	}

	private void storeMessage(final Message m) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					db.addLocalMessage(m);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Storing message took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}
}
