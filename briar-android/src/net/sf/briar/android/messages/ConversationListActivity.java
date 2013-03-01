package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;

import com.google.inject.Inject;

public class ConversationListActivity extends BriarActivity
implements OnClickListener, DatabaseListener {

	private static final Logger LOG =
			Logger.getLogger(ConversationListActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;
	@Inject private MessageFactory messageFactory;

	private ArrayAdapter<ConversationListItem> adapter = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new ConversationListAdapter(this);
		ListView list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1f));
		list.setAdapter(adapter);
		layout.addView(list);

		Button composeButton = new Button(this);
		composeButton.setBackgroundResource(0);
		composeButton.setLayoutParams(new LayoutParams(MATCH_PARENT,
				WRAP_CONTENT));
		composeButton.setCompoundDrawablesWithIntrinsicBounds(0,
				R.drawable.content_new_email, 0, 0);
		composeButton.setText(R.string.compose_button);
		composeButton.setOnClickListener(this);
		layout.addView(composeButton);

		setContentView(layout);

		// Listen for messages being added or removed
		db.addListener(this);
		// Bind to the service so we can wait for the DB to be opened
		bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
		// Load the message headers from the DB
		reloadMessageHeaders();

		// Add some fake messages to the database in a background thread
		// FIXME: Remove this
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// If there are no messages in the DB, create some fake ones
					Collection<PrivateMessageHeader> headers =
							db.getPrivateMessageHeaders();
					if(headers.isEmpty()) {
						if(LOG.isLoggable(INFO))
							LOG.info("Inserting fake contact and messages");
						// Insert a fake contact
						ContactId contactId = db.addContact("Carol");
						// Insert some messages to the contact
						Message m = messageFactory.createPrivateMessage(null,
								"First message's subject",
								"First message's body".getBytes("UTF-8"));
						db.addLocalPrivateMessage(m, contactId);
						db.setStarredFlag(m.getId(), true);
						Thread.sleep(2000);
						m = messageFactory.createPrivateMessage(m.getId(),
								"Second message's subject",
								"Second message's body".getBytes("UTF-8"));
						db.addLocalPrivateMessage(m, contactId);
						db.setReadFlag(m.getId(), true);
					}
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(GeneralSecurityException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				} catch(IOException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		db.removeListener(this);
		unbindService(serviceConnection);
	}

	public void onClick(View view) {
		// FIXME: Hook this button up to an activity
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof MessageAddedEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
			reloadMessageHeaders();
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message removed, reloading");
			reloadMessageHeaders();
		}
	}

	private void reloadMessageHeaders() {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// Wait for the service to be bound and started
					serviceConnection.waitForStartup();
					// Load the contact list from the database
					Collection<Contact> contacts = db.getContacts();
					if(LOG.isLoggable(INFO))
						LOG.info("Loaded " + contacts.size() + " contacts");
					// Load the message headers from the database
					Collection<PrivateMessageHeader> headers =
							db.getPrivateMessageHeaders();
					if(LOG.isLoggable(INFO))
						LOG.info("Loaded " + headers.size() + " headers");
					// Update the conversation list
					updateConversationList(contacts, headers);
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

	private void updateConversationList(final Collection<Contact> contacts,
			final Collection<PrivateMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				adapter.clear();
				for(ConversationListItem i : sortHeaders(contacts, headers))
					adapter.add(i);
				adapter.sort(ConversationListItem.COMPARATOR);
			}
		});
	}

	private List<ConversationListItem> sortHeaders(Collection<Contact> contacts,
			Collection<PrivateMessageHeader> headers) {
		// Group the headers into conversations, one per contact
		Map<ContactId, List<PrivateMessageHeader>> map =
				new HashMap<ContactId, List<PrivateMessageHeader>>();
		for(Contact c : contacts)
			map.put(c.getId(), new ArrayList<PrivateMessageHeader>());
		for(PrivateMessageHeader h : headers) {
			ContactId id = h.getContactId();
			List<PrivateMessageHeader> conversation = map.get(id);
			// Ignore header if the contact was added after db.getContacts()
			if(conversation != null) conversation.add(h);
		}
		// Create a list item for each non-empty conversation
		List<ConversationListItem> list = new ArrayList<ConversationListItem>();
		for(Contact c : contacts) {
			List<PrivateMessageHeader> conversation = map.get(c.getId());
			if(!conversation.isEmpty())
				list.add(new ConversationListItem(c, conversation));
		}
		return list;
	}
}
