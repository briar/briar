package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.BriarService.BriarServiceConnection;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;

import com.google.inject.Inject;

public class ConversationActivity extends BriarActivity
implements DatabaseListener, OnClickListener, OnItemClickListener {

	private static final Logger LOG =
			Logger.getLogger(ConversationActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;

	private ConversationAdapter adapter = null;
	private ListView list = null;
	private String contactName = null;
	private volatile ContactId contactId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		contactName = i.getStringExtra("net.sf.briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();
		setTitle(contactName);
		int id = i.getIntExtra("net.sf.briar.CONTACT_ID", -1);
		if(id == -1) throw new IllegalStateException();
		contactId = new ContactId(id);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new ConversationAdapter(this);
		list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1f));
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
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
	}

	@Override
	public void onResume() {
		super.onResume();
		reloadMessageHeaders();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		db.removeListener(this);
		unbindService(serviceConnection);
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
					// Load the message headers from the database
					Collection<PrivateMessageHeader> headers =
							db.getPrivateMessageHeaders(contactId);
					if(LOG.isLoggable(INFO))
						LOG.info("Loaded " + headers.size() + " headers");
					// Update the conversation
					updateConversation(headers);
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

	private void updateConversation(
			final Collection<PrivateMessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				int firstUnread = -1;
				adapter.clear();
				for(PrivateMessageHeader h : headers) {
					if(firstUnread == -1 && !h.isRead())
						firstUnread = adapter.getCount();
					adapter.add(h);
				}
				adapter.sort(AscendingHeaderComparator.INSTANCE);
				if(firstUnread == -1) list.setSelection(adapter.getCount() - 1);
				else list.setSelection(firstUnread);
			}
		});
	}

	public void onClick(View view) {
		// FIXME: Hook this button up to an activity
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		PrivateMessageHeader item = adapter.getItem(position);
		Intent i = new Intent(this, ReadMessageActivity.class);
		i.putExtra("net.sf.briar.CONTACT_NAME", contactName);
		i.putExtra("net.sf.briar.MESSAGE_ID", item.getId().getBytes());
		i.putExtra("net.sf.briar.CONTENT_TYPE", item.getContentType());
		i.putExtra("net.sf.briar.TIMESTAMP", item.getTimestamp());
		i.putExtra("net.sf.briar.STARRED", item.isStarred());
		startActivity(i);
	}
}
