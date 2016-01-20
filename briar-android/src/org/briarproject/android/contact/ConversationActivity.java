package org.briarproject.android.contact;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.event.ContactConnectedEvent;
import org.briarproject.api.event.ContactDisconnectedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.event.MessagesAckedEvent;
import org.briarproject.api.event.MessagesSentEvent;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
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

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class ConversationActivity extends BriarActivity
		implements EventListener, OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(ConversationActivity.class.getName());

	@Inject private AndroidNotificationManager notificationManager;
	@Inject private ConnectionRegistry connectionRegistry;
	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private Map<MessageId, byte[]> bodyCache = new HashMap<MessageId, byte[]>();
	private ConversationAdapter adapter = null;
	private BriarRecyclerView list = null;
	private EditText content = null;
	private ImageButton sendButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile ContactManager contactManager;
	@Inject private volatile MessagingManager messagingManager;
	@Inject private volatile EventBus eventBus;
	@Inject private volatile PrivateMessageFactory privateMessageFactory;
	private volatile GroupId groupId = null;
	private volatile ContactId contactId = null;
	private volatile String contactName = null;
	private volatile boolean connected = false;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra("briar.GROUP_ID");
		if (b == null) throw new IllegalStateException();
		groupId = new GroupId(b);

		setContentView(R.layout.activity_conversation);

		adapter = new ConversationAdapter(this);
		list = (BriarRecyclerView) findViewById(R.id.conversationView);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_private_messages));

		content = (EditText) findViewById(R.id.contentView);
		sendButton = (ImageButton) findViewById(R.id.sendButton);
		sendButton.setEnabled(false); // Enabled after loading the conversation
		sendButton.setOnClickListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		notificationManager.blockNotification(groupId);
		notificationManager.clearPrivateMessageNotification(groupId);
		loadContactDetails();
		loadHeaders();
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
		notificationManager.unblockNotification(groupId);
		if (isFinishing()) markMessagesRead();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.contact_actions, menu);

		// Adapt icon color to dark action bar
		menu.findItem(R.id.action_social_remove_person).getIcon().setColorFilter(
				getResources().getColor(R.color.action_bar_text),
				PorterDuff.Mode.SRC_IN);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.action_social_remove_person:
				askToRemoveContact();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void loadContactDetails() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					contactId = messagingManager.getContactId(groupId);
					Contact contact = contactManager.getContact(contactId);
					contactName = contact.getAuthor().getName();
					connected = connectionRegistry.isConnected(contactId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Loading contact took " + duration + " ms");
					displayContactDetails();
				} catch (NoSuchContactException e) {
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
					Collection<PrivateMessageHeader> headers =
							messagingManager.getMessageHeaders(contactId);
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

	private void displayHeaders(
			final Collection<PrivateMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				sendButton.setEnabled(true);
				if (headers.isEmpty()) {
					// we have no messages,
					// so let the list know to hide progress bar
					list.showData();
				} else {
					for (PrivateMessageHeader h : headers) {
						ConversationItem item = new ConversationItem(h);
						byte[] body = bodyCache.get(h.getId());
						if (body == null) loadMessageBody(h);
						else item.setBody(body);
						adapter.add(item);
					}
					// Scroll to the bottom
					list.scrollToPosition(adapter.getItemCount() - 1);
				}
			}
		});
	}

	private void loadMessageBody(final PrivateMessageHeader h) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					byte[] body = messagingManager.getMessageBody(h.getId());
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
				int count = adapter.getItemCount();
				for (int i = 0; i < count; i++) {
					ConversationItem item = adapter.getItem(i);
					if (item.getHeader().getId().equals(m)) {
						item.setBody(body);
						adapter.notifyItemChanged(i);
						// Scroll to the bottom
						list.scrollToPosition(count - 1);
						return;
					}
				}
			}
		});
	}

	private void markMessagesRead() {
		List<MessageId> unread = new ArrayList<MessageId>();
		int count = adapter.getItemCount();
		for (int i = 0; i < count; i++) {
			PrivateMessageHeader h = adapter.getItem(i).getHeader();
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
					for (MessageId m : unread)
						messagingManager.setReadFlag(m, true);
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
		} else if (e instanceof MessageValidatedEvent) {
			MessageValidatedEvent m = (MessageValidatedEvent) e;
			if (m.isValid() && m.getMessage().getGroupId().equals(groupId)) {
				LOG.info("Message added, reloading");
				// Mark new incoming messages as read directly
				if (m.isLocal()) loadHeaders();
				else markMessageReadIfNew(m.getMessage());
			}
		} else if (e instanceof MessagesSentEvent) {
			MessagesSentEvent m = (MessagesSentEvent) e;
			if (m.getContactId().equals(contactId)) {
				LOG.info("Messages sent");
				markMessages(m.getMessageIds(), true, false);
			}
		} else if (e instanceof MessagesAckedEvent) {
			MessagesAckedEvent m = (MessagesAckedEvent) e;
			if (m.getContactId().equals(contactId)) {
				LOG.info("Messages acked");
				markMessages(m.getMessageIds(), true, true);
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

	private void markMessageReadIfNew(final Message m) {
		runOnUiThread(new Runnable() {
			public void run() {
				ConversationItem item = adapter.getLastItem();
				if (item != null) {
					// Mark the message read if it's the newest message
					long lastMsgTime = item.getHeader().getTimestamp();
					long newMsgTime = m.getTimestamp();
					if (newMsgTime > lastMsgTime) markNewMessageRead(m);
					else loadHeaders();
				}
			}
		});
	}

	private void markNewMessageRead(final Message m) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					messagingManager.setReadFlag(m.getId(), true);
					loadHeaders();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void markMessages(final Collection<MessageId> messageIds,
			final boolean sent, final boolean seen) {
		runOnUiThread(new Runnable() {
			public void run() {
				Set<MessageId> messages = new HashSet<MessageId>(messageIds);
				int count = adapter.getItemCount();
				for (int i = 0; i < count; i++) {
					ConversationItem item = adapter.getItem(i);
					if (messages.contains(item.getHeader().getId())) {
						item.setSent(sent);
						item.setSeen(seen);
						adapter.notifyItemChanged(i);
					}
				}
			}
		});
	}

	public void onClick(View view) {
		markMessagesRead();
		String message = content.getText().toString();
		if (message.equals("")) return;
		long timestamp = System.currentTimeMillis();
		timestamp = Math.max(timestamp, getMinTimestampForNewMessage());
		createMessage(StringUtils.toUtf8(message), timestamp);
		content.setText("");
		hideSoftKeyboard(content);
	}

	private long getMinTimestampForNewMessage() {
		// Don't use an earlier timestamp than the newest message
		ConversationItem item = adapter.getLastItem();
		return item == null ? 0 : item.getHeader().getTimestamp() + 1;
	}

	private void createMessage(final byte[] body, final long timestamp) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				try {
					storeMessage(privateMessageFactory.createPrivateMessage(
							groupId, timestamp, null, "text/plain", body));
				} catch (GeneralSecurityException e) {
					throw new RuntimeException(e);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	private void storeMessage(final PrivateMessage m) {
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

	private void askToRemoveContact() {
		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeContact();
					}
				};
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ConversationActivity.this);
		builder.setTitle(getString(R.string.dialog_title_delete_contact));
		builder.setMessage(getString(R.string.dialog_message_delete_contact));
		builder.setPositiveButton(android.R.string.ok, okListener);
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void removeContact() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					contactManager.removeContact(contactId);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} finally {
					finishAfterContactRemoved();
				}
			}
		});
	}

	private void finishAfterContactRemoved() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				String deleted = getString(R.string.contact_deleted_toast);
				Toast.makeText(ConversationActivity.this, deleted, LENGTH_SHORT)
						.show();
				finish();
			}
		});
	}
}
