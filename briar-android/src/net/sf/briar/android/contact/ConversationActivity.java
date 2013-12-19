package net.sf.briar.android.contact;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_MATCH;
import static net.sf.briar.android.util.CommonLayoutParams.MATCH_WRAP_1;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import net.sf.briar.R;
import net.sf.briar.android.util.HorizontalBorder;
import net.sf.briar.android.util.ListLoadingProgressBar;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.messaging.GroupId;
import roboguice.activity.RoboActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

public class ConversationActivity extends RoboActivity
implements DatabaseListener, OnClickListener, OnItemClickListener {

	private static final Logger LOG =
			Logger.getLogger(ConversationActivity.class.getName());

	private String contactName = null;
	private ConversationAdapter adapter = null;
	private ListView list = null;
	private ListLoadingProgressBar loading = null;
	private ImageButton composeButton = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;
	private volatile ContactId contactId = null;
	private volatile GroupId groupId = null;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		Intent i = getIntent();
		int id = i.getIntExtra("net.sf.briar.CONTACT_ID", -1);
		if(id == -1) throw new IllegalStateException();
		contactId = new ContactId(id);
		contactName = i.getStringExtra("net.sf.briar.CONTACT_NAME");
		if(contactName == null) throw new IllegalStateException();
		setTitle(contactName);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		adapter = new ConversationAdapter(this);
		list = new ListView(this);
		// Give me all the width and all the unused height
		list.setLayoutParams(MATCH_WRAP_1);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		layout.addView(list);

		// Show a progress bar while the list is loading
		list.setVisibility(GONE);
		loading = new ListLoadingProgressBar(this);
		layout.addView(loading);

		layout.addView(new HorizontalBorder(this));

		composeButton = new ImageButton(this);
		composeButton.setBackgroundResource(0);
		composeButton.setImageResource(R.drawable.content_new_email);
		composeButton.setEnabled(false); // Enabled after loading the headers
		composeButton.setOnClickListener(this);
		layout.addView(composeButton);

		setContentView(layout);
	}

	@Override
	public void onResume() {
		super.onResume();
		db.addListener(this);
		loadHeaders();
	}

	private void loadHeaders() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					groupId = db.getInboxGroup(contactId);
					Collection<MessageHeader> headers =
							db.getInboxMessageHeaders(contactId);
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayHeaders(headers);
				} catch(NoSuchContactException e) {
					if(LOG.isLoggable(INFO)) LOG.info("Contact removed");
					runOnUiThread(new Runnable() {
						public void run() {
							finish();
						}
					});
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

	private void displayHeaders(final Collection<MessageHeader> headers) {
		runOnUiThread(new Runnable() {
			public void run() {
				list.setVisibility(VISIBLE);
				loading.setVisibility(GONE);
				composeButton.setEnabled(true);
				adapter.clear();
				for(MessageHeader h : headers)
					adapter.add(new ConversationItem(h));
				adapter.sort(ConversationItemComparator.INSTANCE);
				adapter.notifyDataSetChanged();
				selectFirstUnread();
			}
		});
	}

	private void selectFirstUnread() {
		int firstUnread = -1, count = adapter.getCount();
		for(int i = 0; i < count; i++) {
			if(!adapter.getItem(i).getHeader().isRead()) {
				firstUnread = i;
				break;
			}
		}
		if(firstUnread == -1) list.setSelection(count - 1);
		else list.setSelection(firstUnread);
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		if(result == ReadPrivateMessageActivity.RESULT_PREV) {
			int position = request - 1;
			if(position >= 0 && position < adapter.getCount())
				displayMessage(position);
		} else if(result == ReadPrivateMessageActivity.RESULT_NEXT) {
			int position = request + 1;
			if(position >= 0 && position < adapter.getCount())
				displayMessage(position);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		db.removeListener(this);
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			ContactRemovedEvent c = (ContactRemovedEvent) e;
			if(c.getContactId().equals(contactId)) {
				if(LOG.isLoggable(INFO)) LOG.info("Contact removed");
				runOnUiThread(new Runnable() {
					public void run() {
						finish();
					}
				});
			}
		} else if(e instanceof MessageAddedEvent) {
			ContactId source = ((MessageAddedEvent) e).getContactId();
			if(source == null || source.equals(contactId)) {
				if(LOG.isLoggable(INFO)) LOG.info("Message added, reloading");
				loadHeaders();
			}
		} else if(e instanceof MessageExpiredEvent) {
			if(LOG.isLoggable(INFO)) LOG.info("Message expired, reloading");
			loadHeaders();
		}
	}

	public void onClick(View view) {
		Intent i = new Intent(this, WritePrivateMessageActivity.class);
		i.putExtra("net.sf.briar.CONTACT_ID", contactId.getInt());
		i.putExtra("net.sf.briar.GROUP_ID", groupId.getBytes());
		startActivity(i);
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		displayMessage(position);
	}

	private void displayMessage(int position) {
		MessageHeader header = adapter.getItem(position).getHeader();
		Intent i = new Intent(this, ReadPrivateMessageActivity.class);
		i.putExtra("net.sf.briar.CONTACT_ID", contactId.getInt());
		i.putExtra("net.sf.briar.CONTACT_NAME", contactName);
		i.putExtra("net.sf.briar.GROUP_ID", header.getGroupId().getBytes());
		i.putExtra("net.sf.briar.AUTHOR_NAME", header.getAuthor().getName());
		i.putExtra("net.sf.briar.MESSAGE_ID", header.getId().getBytes());
		i.putExtra("net.sf.briar.CONTENT_TYPE", header.getContentType());
		i.putExtra("net.sf.briar.TIMESTAMP", header.getTimestamp());
		startActivityForResult(i, position);
	}
}
