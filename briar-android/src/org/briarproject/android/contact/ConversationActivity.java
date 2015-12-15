package org.briarproject.android.contact;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.AuthorId;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.api.db.MessageHeader.State;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.event.ContactConnectedEvent;
import org.briarproject.api.event.ContactDisconnectedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.MessageExpiredEvent;
import org.briarproject.api.event.MessagesAckedEvent;
import org.briarproject.api.event.MessagesSentEvent;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageFactory;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.View.GONE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.contact.ReadPrivateMessageActivity.RESULT_PREV_NEXT;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP_1;

public class ConversationActivity extends BriarActivity
implements EventListener, OnClickListener, OnItemClickListener {

	private static final int REQUEST_READ = 2;
	private static final Logger LOG =
			Logger.getLogger(ConversationActivity.class.getName());

	@Inject private AndroidNotificationManager notificationManager;
	@Inject private ConnectionRegistry connectionRegistry;
	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private Map<MessageId, byte[]> bodyCache = new HashMap<MessageId, byte[]>();
	private TextView empty = null;
	private ConversationAdapter adapter = null;
	private ListView list = null;
	private ProgressBar loading = null;
	private EditText content = null;
	private ImageButton sendButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject private volatile EventBus eventBus;
	@Inject private volatile MessageFactory messageFactory;
	private volatile ContactId contactId = null;
	private volatile String contactName = null;
	private volatile GroupId groupId = null;
	private volatile Group group = null;
	private volatile AuthorId localAuthorId = null;
	private volatile boolean connected;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		int id = i.getIntExtra("briar.CONTACT_ID", -1);
		if (id == -1) throw new IllegalStateException();
		contactId = new ContactId(id);

		Intent data = new Intent();
		data.putExtra("briar.CONTACT_ID", id);
		setResult(RESULT_OK, data);

		setContentView(R.layout.activity_conversation);
		ViewGroup layout = (ViewGroup) findViewById(R.id.layout);
		empty = (TextView) findViewById(R.id.emptyView);

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
		int background = res.getColor(android.R.color.transparent);
		list.setDivider(new ColorDrawable(background));
		list.setDividerHeight(pad);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		list.setEmptyView(empty);
		layout.addView(list, 0);

		// Show a progress bar while the list is loading
		loading = (ProgressBar) findViewById(R.id.listLoadingProgressBar);

		content = (EditText) findViewById(R.id.contentView);
		sendButton = (ImageButton) findViewById(R.id.sendButton);
		sendButton.setEnabled(false); // Enabled after loading the group
		sendButton.setOnClickListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		loadContactAndGroup();
		loadHeaders();
	}

	private void loadContactAndGroup() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Contact contact = db.getContact(contactId);
					contactName = contact.getAuthor().getName();
					localAuthorId = contact.getLocalAuthorId();
					groupId = db.getInboxGroupId(contactId);
					group = db.getGroup(groupId);
					connected = connectionRegistry.isConnected(contactId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO)) {
						LOG.info("Loading contact and group took "
								+ duration + " ms");
					}
					displayContactDetails();
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

	private void displayContactDetails() {
		runOnUiThread(new Runnable() {
			public void run() {
				ActionBar actionBar = getSupportActionBar();
				if (actionBar != null) {
					actionBar.setTitle(contactName);
					if (connected) {
						actionBar.setSubtitle(getString(R.string.online));
					} else {
						actionBar.setSubtitle(getString(R.string.offline));
					}
				}
			}
		});
	}

	private void loadHeaders() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Collection<MessageHeader> headers =
							db.getInboxMessageHeaders(contactId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading headers took " + duration + " ms");
					displayHeaders(headers);
				} catch (NoSuchContactException e) {
					finishOnUiThread();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayHeaders(final Collection<MessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				loading.setVisibility(GONE);
				displayContactDetails();
				sendButton.setEnabled(true);
				adapter.clear();
				if (!headers.isEmpty()) {
					for (MessageHeader h : headers) {
						ConversationItem item = new ConversationItem(h);
						byte[] body = bodyCache.get(h.getId());
						if (body == null) loadMessageBody(h);
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
					if (LOG.isLoggable(INFO))
						LOG.info("Loading message took " + duration + " ms");
					displayMessageBody(h.getId(), body);
				} catch (NoSuchMessageException e) {
					// The item will be removed when we get the event
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
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
				for (int i = 0; i < count; i++) {
					ConversationItem item = adapter.getItem(i);
					if (item.getHeader().getId().equals(m)) {
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
		if (request == REQUEST_READ && result == RESULT_PREV_NEXT) {
			int position = data.getIntExtra("briar.POSITION", -1);
			if (position >= 0 && position < adapter.getCount())
				displayMessage(position);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
		if (isFinishing()) markMessagesRead();
	}

	private void markMessagesRead() {
		notificationManager.clearPrivateMessageNotification(contactId);
		List<MessageId> unread = new ArrayList<MessageId>();
		int count = adapter.getCount();
		for (int i = 0; i < count; i++) {
			MessageHeader h = adapter.getItem(i).getHeader();
			if (!h.isRead()) unread.add(h.getId());
		}
		if (unread.isEmpty()) return;
		if (LOG.isLoggable(INFO))
			LOG.info("Marking " + unread.size() + " messages read");
		markMessagesRead(Collections.unmodifiableList(unread));
	}

	private void markMessagesRead(final Collection<MessageId> unread) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					for (MessageId m : unread) db.setReadFlag(m, true);
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

	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if (c.getContactId().equals(contactId)) {
				LOG.info("Contact removed");
				finishOnUiThread();
			}
		} else if (e instanceof MessageAddedEvent) {
			GroupId g = ((MessageAddedEvent) e).getGroup().getId();
			if (g.equals(groupId)) {
				LOG.info("Message added, reloading");
				loadHeaders();
			}
		} else if (e instanceof MessageExpiredEvent) {
			LOG.info("Message expired, reloading");
			loadHeaders();
		} else if (e instanceof MessagesSentEvent) {
			MessagesSentEvent m = (MessagesSentEvent) e;
			if (m.getContactId().equals(contactId)) {
				LOG.info("Messages sent");
				markMessages(m.getMessageIds(), State.SENT);
			}
		} else if (e instanceof MessagesAckedEvent) {
			MessagesAckedEvent m = (MessagesAckedEvent) e;
			if (m.getContactId().equals(contactId)) {
				LOG.info("Messages acked");
				markMessages(m.getMessageIds(), State.DELIVERED);
			}
		} else if (e instanceof ContactConnectedEvent) {
			ContactConnectedEvent c = (ContactConnectedEvent) e;
			if (c.getContactId().equals(contactId)) {
				LOG.info("Contact connected");
				connected = true;
				displayContactDetails();
			}
		} else if (e instanceof ContactDisconnectedEvent) {
			ContactDisconnectedEvent c = (ContactDisconnectedEvent) e;
			if (c.getContactId().equals(contactId)) {
				LOG.info("Contact disconnected");
				connected = false;
				displayContactDetails();
			}
		}
	}

	private void markMessages(final Collection<MessageId> messageIds, final State state) {
		runOnUiThread(new Runnable() {
			public void run() {
				Set<MessageId> messages = new HashSet<MessageId>(messageIds);
				boolean changed = false;
				int count = adapter.getCount();
				for (int i = 0; i < count; i++) {
					ConversationItem item = adapter.getItem(i);
					if (messages.contains(item.getHeader().getId())) {
						item.setStatus(state);
						changed = true;
					}
				}
				if (changed) adapter.notifyDataSetChanged();
			}
		});
	}

	public void onClick(View view) {
		String message = content.getText().toString();
		if (message.equals("")) return;
		long timestamp = System.currentTimeMillis();
		timestamp = Math.max(timestamp, getMinTimestampForNewMessage());
		createMessage(StringUtils.toUtf8(message), timestamp);
		content.setText("");
		hideSoftKeyboard();
	}

	private long getMinTimestampForNewMessage() {
		// Don't use an earlier timestamp than the newest message
		long timestamp = 0;
		int count = adapter.getCount();
		for (int i = 0; i < count; i++) {
			long t = adapter.getItem(i).getHeader().getTimestamp();
			if (t > timestamp) timestamp = t;
		}
		return timestamp + 1;
	}

	private void createMessage(final byte[] body, final long timestamp) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				try {
					Message m = messageFactory.createAnonymousMessage(null,
							group, "text/plain", timestamp, body);
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
					db.addLocalMessage(m);
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
		i.putExtra("briar.MIN_TIMESTAMP", getMinTimestampForNewMessage());
		i.putExtra("briar.POSITION", position);
		startActivityForResult(i, REQUEST_READ);
	}
}
