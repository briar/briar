package net.sf.briar.android.messages;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
import android.widget.ImageButton;
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

	private ContactId contactId = null;
	private String contactName = null;
	private ConversationAdapter adapter = null;
	private ListView list = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);

		Intent i = getIntent();
		int id = i.getIntExtra("net.sf.briar.CONTACT_ID", -1);
		if(id == -1) throw new IllegalStateException();
		contactId = new ContactId(id);
		contactName = i.getStringExtra("net.sf.briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();

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

		ImageButton composeButton = new ImageButton(this);
		composeButton.setPadding(10, 10, 10, 10);
		composeButton.setBackgroundResource(0);
		composeButton.setImageResource(R.drawable.content_new_email);
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
		final ContactId contactId = this.contactId;
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

	private void updateConversation(Collection<PrivateMessageHeader> headers) {
		final List<PrivateMessageHeader> sort =
				new ArrayList<PrivateMessageHeader>(headers);
		Collections.sort(sort, AscendingHeaderComparator.INSTANCE);
		runOnUiThread(new Runnable() {
			public void run() {
				int firstUnread = -1;
				adapter.clear();
				for(PrivateMessageHeader h : sort) {
					if(firstUnread == -1 && !h.isRead())
						firstUnread = adapter.getCount();
					adapter.add(h);
				}
				if(firstUnread == -1) list.setSelection(adapter.getCount() - 1);
				else list.setSelection(firstUnread);
			}
		});
	}

	public void onClick(View view) {
		Intent i = new Intent(this, WriteMessageActivity.class);
		i.putExtra("net.sf.briar.CONTACT_ID", contactId.getInt());
		i.putExtra("net.sf.briar.CONTACT_NAME", contactName);
		startActivity(i);
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		showMessage(position);
	}

	private void showMessage(int position) {
		PrivateMessageHeader item = adapter.getItem(position);
		Intent i = new Intent(this, ReadMessageActivity.class);
		i.putExtra("net.sf.briar.CONTACT_ID", contactId.getInt());
		i.putExtra("net.sf.briar.CONTACT_NAME", contactName);
		i.putExtra("net.sf.briar.MESSAGE_ID", item.getId().getBytes());
		i.putExtra("net.sf.briar.CONTENT_TYPE", item.getContentType());
		i.putExtra("net.sf.briar.TIMESTAMP", item.getTimestamp());
		i.putExtra("net.sf.briar.FIRST", position == 0);
		i.putExtra("net.sf.briar.LAST", position == adapter.getCount() - 1);
		i.putExtra("net.sf.briar.STARRED", item.isStarred());
		startActivityForResult(i, position);
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		if(result == ReadMessageActivity.RESULT_PREV) {
			int position = request - 1;
			if(position >= 0 && position < adapter.getCount())
				showMessage(position);
		} else if(result == ReadMessageActivity.RESULT_NEXT) {
			int position = request + 1;
			if(position >= 0 && position < adapter.getCount())
				showMessage(position);
		}
	}
}
