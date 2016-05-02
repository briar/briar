package org.briarproject.android.forum;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.AndroidComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.forum.AvailableForumsAdapter.AvailableForumClickListener;

public class AvailableForumsActivity extends BriarActivity
		implements EventListener, AvailableForumClickListener {

	private static final Logger LOG =
			Logger.getLogger(AvailableForumsActivity.class.getName());

	private AvailableForumsAdapter adapter;

	// Fields that are accessed from background threads must be volatile
	@Inject protected volatile ForumManager forumManager;
	@Inject protected volatile ForumSharingManager forumSharingManager;
	@Inject protected volatile EventBus eventBus;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_available_forums);

		adapter = new AvailableForumsAdapter(this, this);
		BriarRecyclerView list =
				(BriarRecyclerView) findViewById(R.id.availableForumsView);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
	}

	@Override
	public void injectActivity(AndroidComponent component) {
		component.inject(this);
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
					Collection<ForumContacts> available = new ArrayList<>();
					long now = System.currentTimeMillis();
					for (Forum f : forumSharingManager.getAvailableForums()) {
						try {
							Collection<Contact> c =
									forumSharingManager.getSharedBy(f.getId());
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
					adapter.clear();
					List<AvailableForumsItem> list =
							new ArrayList<>(available.size());
					for (ForumContacts f : available)
						list.add(new AvailableForumsItem(f));
					adapter.addAll(list);
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
		if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, reloading");
			loadForums();
		} else if (e instanceof GroupAddedEvent) {
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
		} else if (e instanceof ForumInvitationReceivedEvent) {
			LOG.info("Available forums updated, reloading");
			loadForums();
		}
	}

	public void onItemClick(AvailableForumsItem item, boolean accept) {
		respondToInvitation(item.getForum(), accept);

		// show toast
		int res = R.string.forum_declined_toast;
		if (accept) res = R.string.forum_joined_toast;
		Toast.makeText(this, res, LENGTH_SHORT).show();
	}

	private void respondToInvitation(final Forum f, final boolean accept) {
		runOnDbThread(new Runnable() {
			public void run() {
				try {
					forumSharingManager.respondToInvitation(f, accept);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
				loadForums();
			}
		});
	}

}
