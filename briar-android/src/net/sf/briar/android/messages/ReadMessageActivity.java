package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_VERTICAL;
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
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.BundleEncrypter;
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
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.inject.Inject;

public class ReadMessageActivity extends BriarActivity
implements OnClickListener {

	static final int RESULT_REPLY = RESULT_FIRST_USER;
	static final int RESULT_PREV = RESULT_FIRST_USER + 1;
	static final int RESULT_NEXT = RESULT_FIRST_USER + 2;

	private static final Logger LOG =
			Logger.getLogger(ReadMessageActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private BundleEncrypter bundleEncrypter;
	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;

	private ContactId contactId = null;
	private String contactName = null;
	private MessageId messageId = null;
	private boolean first, last, starred, read;
	private ImageButton starButton = null, readButton = null;
	private ImageButton prevButton = null, nextButton = null;
	private ImageButton replyButton = null;
	private TextView content = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		int cid = i.getIntExtra("net.sf.briar.CONTACT_ID", -1);
		if(cid == -1) throw new IllegalStateException();
		contactId = new ContactId(cid);
		contactName = i.getStringExtra("net.sf.briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();
		byte[] mid = i.getByteArrayExtra("net.sf.briar.MESSAGE_ID");
		if(mid == null) throw new IllegalStateException();
		messageId = new MessageId(mid);
		String contentType = i.getStringExtra("net.sf.briar.CONTENT_TYPE");
		if(contentType == null) throw new IllegalStateException();
		long timestamp = i.getLongExtra("net.sf.briar.TIMESTAMP", -1);
		if(timestamp == -1) throw new IllegalStateException();
		first = i.getBooleanExtra("net.sf.briar.FIRST", false);
		last = i.getBooleanExtra("net.sf.briar.LAST", false);

		if(state != null && bundleEncrypter.decrypt(state)) {
			starred = state.getBoolean("net.sf.briar.STARRED");
			read = state.getBoolean("net.sf.briar.READ");
		} else {
			starred = i.getBooleanExtra("net.sf.briar.STARRED", false);
			read = false;
			final MessageId id = messageId;
			dbExecutor.execute(new Runnable() {
				public void run() {
					try {
						serviceConnection.waitForStartup();
						db.setReadFlag(id, true);
						runOnUiThread(new Runnable() {
							public void run() {
								setRead(true);
							}
						});
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

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		layout.setOrientation(VERTICAL);

		ScrollView scrollView = new ScrollView(this);
		// Give me all the width and all the unused height
		scrollView.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT,
				1));

		LinearLayout message = new LinearLayout(this);
		message.setOrientation(VERTICAL);

		LinearLayout header = new LinearLayout(this);
		header.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		header.setOrientation(HORIZONTAL);
		header.setGravity(CENTER_VERTICAL);

		TextView name = new TextView(this);
		// Give me all the unused width
		name.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1));
		name.setTextSize(18);
		name.setPadding(10, 0, 0, 0);
		String format = getResources().getString(R.string.message_from);
		name.setText(String.format(format, contactName));
		header.addView(name);

		TextView date = new TextView(this);
		date.setTextSize(14);
		date.setPadding(0, 0, 10, 0);
		long now = System.currentTimeMillis();
		date.setText(DateUtils.formatSameDayTime(timestamp, now, SHORT, SHORT));
		header.addView(date);
		message.addView(header);

		if(contentType.equals("text/plain")) {
			// Load and display the message body
			content = new TextView(this);
			content.setPadding(10, 10, 10, 10);
			message.addView(content);
			loadMessageBody();
		}
		scrollView.addView(message);
		layout.addView(scrollView);

		View line = new View(this);
		line.setLayoutParams(new LayoutParams(MATCH_PARENT, 5));
		int colour = getResources().getColor(R.color.ButtonBarBorder);
		line.setBackgroundColor(colour);
		layout.addView(line);

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER);

		starButton = new ImageButton(this);
		starButton.setPadding(10, 10, 10, 10);
		starButton.setBackgroundResource(0);
		if(starred) starButton.setImageResource(R.drawable.rating_important);
		else starButton.setImageResource(R.drawable.rating_not_important);
		starButton.setOnClickListener(this);
		footer.addView(starButton);

		readButton = new ImageButton(this);
		readButton.setPadding(10, 10, 10, 10);
		readButton.setBackgroundResource(0);
		if(read) readButton.setImageResource(R.drawable.content_unread);
		else readButton.setImageResource(R.drawable.content_read);
		readButton.setOnClickListener(this);
		footer.addView(readButton);

		prevButton = new ImageButton(this);
		prevButton.setPadding(10, 10, 10, 10);
		prevButton.setBackgroundResource(0);
		prevButton.setImageResource(R.drawable.navigation_previous_item);
		prevButton.setOnClickListener(this);
		prevButton.setEnabled(!first);
		footer.addView(prevButton);

		nextButton = new ImageButton(this);
		nextButton.setPadding(10, 10, 10, 10);
		nextButton.setBackgroundResource(0);
		nextButton.setImageResource(R.drawable.navigation_next_item);
		nextButton.setOnClickListener(this);
		nextButton.setEnabled(!last);
		footer.addView(nextButton);

		replyButton = new ImageButton(this);
		replyButton.setPadding(10, 10, 10, 10);
		replyButton.setBackgroundResource(0);
		replyButton.setImageResource(R.drawable.social_reply);
		replyButton.setOnClickListener(this);
		footer.addView(replyButton);
		layout.addView(footer);

		setContentView(layout);

		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	private void loadMessageBody() {
		final MessageId messageId = this.messageId;
		final TextView content = this.content;
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					serviceConnection.waitForStartup();
					byte[] body = db.getMessageBody(messageId);
					final String text = new String(body, "UTF-8");
					runOnUiThread(new Runnable() {
						public void run() {
							content.setText(text);
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
	public void onSaveInstanceState(Bundle state) {
		state.putBoolean("net.sf.briar.STARRED", starred);
		state.putBoolean("net.sf.briar.READ", read);
		bundleEncrypter.encrypt(state);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		if(view == starButton) {
			final MessageId messageId = this.messageId;
			final boolean starred = !this.starred;
			dbExecutor.execute(new Runnable() {
				public void run() {
					try {
						serviceConnection.waitForStartup();
						db.setStarredFlag(messageId, starred);
						runOnUiThread(new Runnable() {
							public void run() {
								setStarred(starred);
							}
						});
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
		} else if(view == readButton) {
			final MessageId messageId = this.messageId;
			final boolean read = !this.read;
			dbExecutor.execute(new Runnable() {
				public void run() {
					try {
						serviceConnection.waitForStartup();
						db.setReadFlag(messageId, read);
						runOnUiThread(new Runnable() {
							public void run() {
								setRead(read);
							}
						});
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
		} else if(view == prevButton) {
			setResult(RESULT_PREV);
			finish();
		} else if(view == nextButton) {
			setResult(RESULT_NEXT);
			finish();
		} else if(view == replyButton) {
			Intent i = new Intent(this, WriteMessageActivity.class);
			i.putExtra("net.sf.briar.CONTACT_ID", contactId.getInt());
			i.putExtra("net.sf.briar.CONTACT_NAME", contactName);
			i.putExtra("net.sf.briar.PARENT_ID", messageId.getBytes());
			startActivity(i);
			setResult(RESULT_REPLY);
			finish();
		}
	}

	private void setStarred(boolean starred) {
		this.starred = starred;
		if(starred) starButton.setImageResource(R.drawable.rating_important);
		else starButton.setImageResource(R.drawable.rating_not_important);
	}

	private void setRead(boolean read) {
		this.read = read;
		if(read) readButton.setImageResource(R.drawable.content_unread);
		else readButton.setImageResource(R.drawable.content_read);
	}
}
