package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.RIGHT;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static java.text.DateFormat.SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.messaging.MessageId;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.google.inject.Inject;

public class ReadMessageActivity extends BriarActivity
implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(ReadMessageActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;

	private MessageId messageId = null;
	private boolean starred = false;
	private ImageButton starButton = null, replyButton = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		String contactName = i.getStringExtra("net.sf.briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();
		setTitle(contactName);
		byte[] id = i.getByteArrayExtra("net.sf.briar.MESSAGE_ID");
		if(id == null) throw new IllegalStateException();
		messageId = new MessageId(id);
		String contentType = i.getStringExtra("net.sf.briar.CONTENT_TYPE");
		if(contentType == null) throw new IllegalStateException();
		long timestamp = i.getLongExtra("net.sf.briar.TIMESTAMP", -1);
		if(timestamp == -1) throw new IllegalStateException();
		starred = i.getBooleanExtra("net.sf.briar.STARRED", false);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		layout.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		starButton = new ImageButton(this);
		starButton.setPadding(5, 5, 5, 5);
		starButton.setBackgroundResource(0);
		if(starred) starButton.setImageResource(R.drawable.rating_important);
		else starButton.setImageResource(R.drawable.rating_not_important);
		starButton.setOnClickListener(this);
		header.addView(starButton);

		replyButton = new ImageButton(this);
		replyButton.setPadding(5, 5, 5, 5);
		replyButton.setBackgroundResource(0);
		replyButton.setImageResource(R.drawable.social_reply);
		replyButton.setOnClickListener(this);
		header.addView(replyButton);

		TextView date = new TextView(this);
		// Give me all the unused width
		date.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1));
		date.setTextSize(14);
		date.setPadding(10, 0, 10, 0);
		date.setGravity(RIGHT);
		long now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(timestamp, now, SHORT, SHORT));
		header.addView(date);
		layout.addView(header);

		if(contentType.equals("text/plain")) {
			// Load and display the message body
			TextView content = new TextView(this);
			content.setPadding(10, 10, 10, 10);
			layout.addView(content);
			loadMessageBody(messageId, content);
		}

		setContentView(layout);

		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	private void loadMessageBody(final MessageId id, final TextView view) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Load the message body from the database
					byte[] body = db.getMessageBody(id);
					final String text = new String(body, "UTF-8");
					// Display the message body
					runOnUiThread(new Runnable() {
						public void run() {
							view.setText(text);
						}
					});
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				} catch(UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		final MessageId id = messageId;
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Mark the message as read
					db.setReadFlag(id, true);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	@Override
	public void onClick(View view) {
		if(view == starButton) {
			final MessageId id = messageId;
			final boolean starredNow = !starred;
			dbExecutor.execute(new Runnable() {
				public void run() {
					try {
						db.setStarredFlag(id, starredNow);
					} catch(DbException e) {
						if(LOG.isLoggable(WARNING))
							LOG.log(WARNING, e.toString(), e);
					}
				}
			});
			starred = starredNow;
			if(starred)
				starButton.setImageResource(R.drawable.rating_important);
			else starButton.setImageResource(R.drawable.rating_not_important);
		} else if(view == replyButton) {
			// FIXME: Hook this up to an activity
		}
	}
}
