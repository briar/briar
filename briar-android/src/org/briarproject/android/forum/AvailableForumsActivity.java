package org.briarproject.android.forum;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.ListLoadingProgressBar;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

public class AvailableForumsActivity extends BriarActivity
implements EventListener, OnItemClickListener {

	private static final Logger LOG =
			Logger.getLogger(AvailableForumsActivity.class.getName());

	private AvailableForumsAdapter adapter = null;
	private ListView list = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile ForumManager forumManager;
	@Inject private volatile EventBus eventBus;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		adapter = new AvailableForumsAdapter(this);
		list = new ListView(this);
		list.setLayoutParams(MATCH_MATCH);
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);

		// Show a progress bar while the list is loading
		ListLoadingProgressBar loading = new ListLoadingProgressBar(this);
		setContentView(loading);
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		loadForums();
	}

	private void loadForums() {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					Collection<ForumContacts> available =
							new ArrayList<ForumContacts>();
					long now = System.currentTimeMillis();
					for (Forum f : forumManager.getAvailableForums()) {
						try {
							GroupId id = f.getId();
							Collection<Contact> c =
									forumManager.getSubscribers(id);
							available.add(new ForumContacts(f, c));
						} catch (NoSuchGroupException e) {
							// Continue
						}
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayForums(available);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayForums(final Collection<ForumContacts> available) {
		runOnUiThread(new Runnable() {
			public void run() {
				if (available.isEmpty()) {
					LOG.info("No forums available, finishing");
					finish();
				} else {
					setContentView(list);
					adapter.clear();
					for (ForumContacts f : available)
						adapter.add(new AvailableForumsItem(f));
					adapter.sort(AvailableForumsItemComparator.INSTANCE);
				}
			}
		});
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
	}

	public void eventOccurred(Event e) {
		// TODO: What other events are needed here?
		if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			if (g.getGroup().getClientId().equals(forumManager.getClientId())) {
				LOG.info("Forum added, reloading");
				loadForums();
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getClientId().equals(forumManager.getClientId())) {
				LOG.info("Forum removed, reloading");
				loadForums();
			}
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		AvailableForumsItem item = adapter.getItem(position);
		Collection<ContactId> visible = new ArrayList<ContactId>();
		for (Contact c : item.getContacts()) visible.add(c.getId());
		addSubscription(item.getForum(), visible);
		String subscribed = getString(R.string.subscribed_toast);
		Toast.makeText(this, subscribed, LENGTH_SHORT).show();
	}

	private void addSubscription(final Forum f,
			final Collection<ContactId> visible) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					forumManager.addForum(f);
					forumManager.setVisibility(f.getId(), visible);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}
