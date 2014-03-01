package org.briarproject.android.contact;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.contact.ReadPrivateMessageActivity.RESULT_PREV_NEXT;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP_1;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.HorizontalBorder;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.AuthorId;
import org.briarproject.api.ContactId;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.MessageExpiredEvent;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageFactory;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.util.StringUtils;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ConversationActivity extends BriarActivity
implements EventListener, OnClickListener, OnItemClickListener {

	private static final int REQUEST_READ = 2;
	private static final Logger LOG =
			Logger.getLogger(ConversationActivity.class.getName());

	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private Map<MessageId, byte[]> bodyCache = new HashMap<MessageId, byte[]>();
	private String contactName = null;
	private TextView empty = null;
	private ConversationAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private EditText content = null;
	private ImageButton sendButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject private volatile MessageFactory messageFactory;
	private volatile ContactId contactId = null;
	private volatile GroupId groupId = null;
	private volatile Group group = null;
	private volatile AuthorId localAuthorId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		int id = i.getIntExtra("briar.CONTACT_ID", -1);
		if(id == -1) throw new IllegalStateException();
		contactId = new ContactId(id);
		contactName = i.getStringExtra("briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();
		setTitle(contactName);
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if(b == null) throw new IllegalStateException();
		groupId = new GroupId(b);
		b = i.getByteArrayExtra("briar.LOCAL_AUTHOR_ID");
		if(b == null) throw new IllegalStateException();
		localAuthorId = new AuthorId(b);

		Intent data = new Intent();
		data.putExtra("briar.CONTACT_ID", id);
		setResult(RESULT_OK, data);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);

		empty = new TextView(this);
		empty.setLayoutParams(MATCH_WRAP_1);
		empty.setGravity(CENTER);
		empty.setTextSize(18);
		empty.setText(R.string.no_private_messages);
		empty.setVisibility(GONE);
		layout.addView(empty);

		adapter = new ConversationAdapter(this);
		list = new ListView(this) {
			@Override
			public void onSizeChanged(int w, int h, int oldw, int oldh) {
				// Scroll to the bottom when the keyboard is shown
				super.onSizeChanged(w, h, oldw, oldh);
				setSelection(getCount() - 1);
			}
		};
		list.setLayoutParams(MATCH_WRAP_1);
		int pad = LayoutUtils.getPadding(this);
		list.setPadding(0, pad, 0, pad);
		list.setClipToPadding(false);
		// Make the dividers the same colour as the background
		Resources res = getResources();
		int background = res.getColor(R.color.window_background);
		list.setDivider(new ColorDrawable(background));
		list.setDividerHeight(pad);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		list.setVisibility(GONE);
		layout.addView(list);

		// Show a progress bar while the list is loading
		loading = new ListLoadingProgressBar(this);
		layout.addView(loading);

		layout.addView(new HorizontalBorder(this));

		LinearLayout footer = new LinearLayout(this);
		footer.setLayoutParams(MATCH_WRAP);
		footer.setOrientation(HORIZONTAL);
		footer.setGravity(CENTER_VERTICAL);
		footer.setPadding(pad, 0, 0, 0);
		footer.setBackgroundColor(res.getColor(R.color.button_bar_background));

		content = new EditText(this);
		content.setId(1);
		content.setLayoutParams(WRAP_WRAP_1);
		int inputType = TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| TYPE_TEXT_FLAG_CAP_SENTENCES;
		content.setInputType(inputType);
		content.setHint(R.string.private_message_hint);
		footer.addView(content);

		sendButton = new ImageButton(this);
		sendButton.setId(2);
		sendButton.setBackgroundResource(0);
		sendButton.setImageResource(R.drawable.social_send_now);
		sendButton.setEnabled(false); // Enabled after loading the group
		sendButton.setOnClickListener(this);
		footer.addView(sendButton);
		layout.addView(footer);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		loadHeadersAndGroup();
	}

	private void loadHeadersAndGroup() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<MessageHeader> headers =
							db.getInboxMessageHeaders(contactId);
					group = db.getGroup(groupId);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayHeaders(headers);
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

	private void displayHeaders(final Collection<MessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				loading.setVisibility(GONE);
				sendButton.setEnabled(true);
				adapter.clear();
				if(headers.isEmpty()) {
					empty.setVisibility(VISIBLE);
					list.setVisibility(GONE);
				} else {
					empty.setVisibility(GONE);
					list.setVisibility(VISIBLE);
					for(MessageHeader h : headers) {
						ConversationItem item = new ConversationItem(h);
						byte[] body = bodyCache.get(h.getId());
						if(body == null) loadMessageBody(h);
						else item.setBody(body);
						adapter.add(item);
					}
					adapter.sort(ConversationItemComparator.INSTANCE);
					// Scroll to the bottom
					list.setSelection(adapter.getCount() - 1);
				}
				adapter.notifyDataSetChanged();
			}
		});
	}

	private void loadMessageBody(final MessageHeader h) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					byte[] body = db.getMessageBody(h.getId());
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Loading message took " + duration + " ms");
					displayMessageBody(h.getId(), body);
				} catch(NoSuchMessageException e) {
					// The item will be removed when we get the event
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayMessageBody(final MessageId m, final byte[] body) {
		runOnUiThread(new Runnable() {
			public void run() {
				bodyCache.put(m, body);
				int count = adapter.getCount();
				for(int i = 0; i < count; i++) {
					ConversationItem item = adapter.getItem(i);
					if(item.getHeader().getId().equals(m)) {
						item.setBody(body);
						adapter.notifyDataSetChanged();
						// Scroll to the bottom
						list.setSelection(count - 1);
						return;
					}
				}
			}
		});
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if(request == REQUEST_READ && result == RESULT_PREV_NEXT) {
			int position = data.getIntExtra("briar.POSITION", -1);
			if(position >= 0 && position < adapter.getCount())
				displayMessage(position);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		db.removeListener(this);
		if(isFinishing()) markMessagesRead();
	}

	private void markMessagesRead() {
		List<MessageId> unread = new ArrayList<MessageId>();
		int count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			MessageHeader h = adapter.getItem(i).getHeader();
			if(!h.isRead()) unread.add(h.getId());
		}
		if(unread.isEmpty()) return;
		if(LOG.isLoggable(INFO))
			LOG.info("Marking " + unread.size() + " messages read");
		markMessagesRead(Collections.unmodifiableList(unread));
	}

	private void markMessagesRead(final Collection<MessageId> unread) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					for(MessageId m : unread) db.setReadFlag(m, true);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Marking read took " + duration + " ms");
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	public void eventOccurred(Event e) {
		if(e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if(c.getContactId().equals(contactId)) {
				if(LOG.isLoggable(INFO)) LOG.info("Contact removed");
				finishOnUiThread();
			}
		} else if(e instanceof MessageAddedEvent) {
			GroupId g = ((MessageAddedEvent) e).getGroup().getId();
			if(g.equals(groupId)) {
				if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
				loadHeadersAndGroup();
			}
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message expired, reloading");
			loadHeadersAndGroup();
		}
	}

	public void onClick(View view) {
		String message = content.getText().toString();
		if(message.equals("")) return;
		// Don't use an earlier timestamp than the newest message
		long timestamp = System.currentTimeMillis();
		int count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			long time = adapter.getItem(i).getHeader().getTimestamp() + 1;
			if(time > timestamp) timestamp = time;
		}
		createMessage(StringUtils.toUtf8(message), timestamp);
		Toast.makeText(this, R.string.message_sent_toast, LENGTH_SHORT).show();
		content.setText("");
		// Hide the soft keyboard
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).toggleSoftInput(HIDE_IMPLICIT_ONLY, 0);
	}

	private void createMessage(final byte[] body, final long timestamp) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				try {
					Message m = messageFactory.createAnonymousMessage(null,
							group, "text/plain", timestamp, body);
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

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		displayMessage(position);
	}

	private void displayMessage(int position) {
		ConversationItem item = adapter.getItem(position);
		MessageHeader header = item.getHeader();
		Intent i = new Intent(this, ReadPrivateMessageActivity.class);
		i.putExtra("briar.CONTACT_ID", contactId.getInt());
		i.putExtra("briar.CONTACT_NAME", contactName);
		i.putExtra("briar.GROUP_ID", groupId.getBytes());
		i.putExtra("briar.LOCAL_AUTHOR_ID", localAuthorId.getBytes());
		i.putExtra("briar.AUTHOR_NAME", header.getAuthor().getName());
		i.putExtra("briar.MESSAGE_ID", header.getId().getBytes());
		i.putExtra("briar.CONTENT_TYPE", header.getContentType());
		i.putExtra("briar.TIMESTAMP", header.getTimestamp());
		i.putExtra("briar.POSITION", position);
		startActivityForResult(i, REQUEST_READ);
	}
}
