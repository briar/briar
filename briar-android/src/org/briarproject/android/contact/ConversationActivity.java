package org.briarproject.android.contact;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.api.android.AndroidNotificationManager;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DbException;
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
import org.briarproject.api.event.MessagesAckedEvent;
import org.briarproject.api.event.MessagesSentEvent;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateConversation;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.api.messaging.PrivateMessageHeader.Status;
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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.contact.ReadPrivateMessageActivity.RESULT_PREV_NEXT;
import static org.briarproject.api.messaging.PrivateMessageHeader.Status.DELIVERED;
import static org.briarproject.api.messaging.PrivateMessageHeader.Status.SENT;

public class ConversationActivity extends BriarActivity
		implements EventListener, OnClickListener {

	private static final int REQUEST_READ = 2;
	private static final Logger LOG =
			Logger.getLogger(ConversationActivity.class.getName());

	@Inject private AndroidNotificationManager notificationManager;
	@Inject private ConnectionRegistry connectionRegistry;
	@Inject @CryptoExecutor private Executor cryptoExecutor;
	private Map<MessageId, byte[]> bodyCache = new HashMap<MessageId, byte[]>();
	private TextView empty = null;
	private ProgressBar loading = null;
	private ConversationAdapter adapter = null;
	private RecyclerView list = null;
	private EditText content = null;
	private ImageButton sendButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile ContactManager contactManager;
	@Inject private volatile MessagingManager messagingManager;
	@Inject private volatile EventBus eventBus;
	@Inject private volatile PrivateMessageFactory privateMessageFactory;
	private volatile ContactId contactId = null;
	private volatile String contactName = null;
	private volatile GroupId groupId = null;
	private volatile PrivateConversation conversation = null;
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
		empty.setVisibility(GONE);
		// Show a progress bar while the list is loading
		loading = (ProgressBar) findViewById(R.id.listLoadingProgressBar);
		loading.setVisibility(VISIBLE);

		adapter = new ConversationAdapter(this);
		list = (RecyclerView) findViewById(R.id.conversationView);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
		list.setVisibility(GONE);
		// scroll down when opening keyboard
		list.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v,
					int left, int top, int right, int bottom,
					int oldLeft, int oldTop, int oldRight, int oldBottom) {
				if (bottom < oldBottom) {
					list.postDelayed(new Runnable() {
						@Override
						public void run() {
							list.scrollToPosition(adapter.getItemCount() - 1);
						}
					}, 100);
				}
			}
		});

		content = (EditText) findViewById(R.id.contentView);
		sendButton = (ImageButton) findViewById(R.id.sendButton);
		sendButton.setEnabled(false); // Enabled after loading the conversation
		sendButton.setOnClickListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		loadContactAndGroup();
		loadHeaders();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.contact_actions, menu);

		// adapt icon color to dark action bar
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

	private void loadContactAndGroup() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					long now = System.currentTimeMillis();
					Contact contact = contactManager.getContact(contactId);
					contactName = contact.getAuthor().getName();
					localAuthorId = contact.getLocalAuthorId();
					groupId = messagingManager.getConversationId(contactId);
					conversation = messagingManager.getConversation(groupId);
					connected = connectionRegistry.isConnected(contactId);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO)) {
						LOG.info("Loading contact and conversation took "
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
				loading.setVisibility(GONE);
				sendButton.setEnabled(true);
				if (!headers.isEmpty()) {
					list.setVisibility(VISIBLE);
					empty.setVisibility(GONE);
					for (PrivateMessageHeader h : headers) {
						ConversationItem item = new ConversationItem(h);
						byte[] body = bodyCache.get(h.getId());
						if (body == null) loadMessageBody(h);
						else item.setBody(body);
						adapter.add(item);
					}
					// Scroll to the bottom
					list.scrollToPosition(adapter.getItemCount() - 1);
				} else {
					empty.setVisibility(VISIBLE);
					list.setVisibility(GONE);
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

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_READ && result == RESULT_PREV_NEXT) {
			int position = data.getIntExtra("briar.POSITION", -1);
			if (position >= 0 && position < adapter.getItemCount())
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
		} else if (e instanceof MessageAddedEvent) {
			GroupId g = ((MessageAddedEvent) e).getGroupId();
			if (g.equals(groupId)) {
				LOG.info("Message added, reloading");
				// TODO: find a way of not needing to reload the entire
				// conversation just because one message was added
				loadHeaders();
			}
		} else if (e instanceof MessagesSentEvent) {
			MessagesSentEvent m = (MessagesSentEvent) e;
			if (m.getContactId().equals(contactId)) {
				LOG.info("Messages sent");
				markMessages(m.getMessageIds(), SENT);
			}
		} else if (e instanceof MessagesAckedEvent) {
			MessagesAckedEvent m = (MessagesAckedEvent) e;
			if (m.getContactId().equals(contactId)) {
				LOG.info("Messages acked");
				markMessages(m.getMessageIds(), DELIVERED);
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

	private void markMessages(final Collection<MessageId> messageIds,
			final Status status) {
		runOnUiThread(new Runnable() {
			public void run() {
				Set<MessageId> messages = new HashSet<MessageId>(messageIds);
				int count = adapter.getItemCount();
				for (int i = 0; i < count; i++) {
					ConversationItem item = adapter.getItem(i);
					if (messages.contains(item.getHeader().getId())) {
						item.setStatus(status);
						adapter.notifyItemChanged(i);
					}
				}
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
		int count = adapter.getItemCount();
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
					Message m = privateMessageFactory.createPrivateMessage(null,
							conversation, "text/plain", timestamp, body);
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

	private void displayMessage(int position) {
		ConversationItem item = adapter.getItem(position);
		PrivateMessageHeader header = item.getHeader();
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

	private void askToRemoveContact() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				DialogInterface.OnClickListener okListener =
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								removeContact();
							}
						};

				AlertDialog.Builder builder =
						new AlertDialog.Builder(ConversationActivity.this);
				builder.setTitle(
						getString(R.string.dialog_title_delete_contact));
				builder.setMessage(
						getString(R.string.dialog_message_delete_contact));
				builder.setPositiveButton(android.R.string.ok, okListener);
				builder.setNegativeButton(android.R.string.cancel, null);
				builder.show();
			}
		});
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
