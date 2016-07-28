package org.briarproject.android.forum;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
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
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.forum.ForumInvitationAdapter.AvailableForumClickListener;

public class ForumInvitationsActivity extends BriarActivity
		implements EventListener, AvailableForumClickListener {

	private static final Logger LOG =
			Logger.getLogger(ForumInvitationsActivity.class.getName());

	private ForumInvitationAdapter adapter;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumManager forumManager;
	@Inject
	protected volatile ForumSharingManager forumSharingManager;
	@Inject
	protected volatile EventBus eventBus;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_available_forums);

		adapter = new ForumInvitationAdapter(this, this);
		BriarRecyclerView list =
				(BriarRecyclerView) findViewById(R.id.availableForumsView);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
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
			@Override
			public void run() {
				try {
					Collection<ForumInvitationItem> forums = new ArrayList<>();
					long now = System.currentTimeMillis();
					for (Forum f : forumSharingManager.getInvited()) {
						boolean subscribed;
						try {
							forumManager.getForum(f.getId());
							subscribed = true;
						} catch (NoSuchGroupException e) {
							subscribed = false;
						}
						Collection<Contact> c =
								forumSharingManager.getSharedBy(f.getId());
						forums.add(
								new ForumInvitationItem(f, subscribed, c));
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayForums(forums);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayForums(final Collection<ForumInvitationItem> forums) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (forums.isEmpty()) {
					LOG.info("No forums available, finishing");
					finish();
				} else {
					adapter.addAll(forums);
				}
			}
		});
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
	}

	@Override
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

	@Override
	public void onItemClick(ForumInvitationItem item, boolean accept) {
		respondToInvitation(item, accept);

		// show toast
		int res = R.string.forum_declined_toast;
		if (accept) res = R.string.forum_joined_toast;
		Toast.makeText(this, res, LENGTH_SHORT).show();

		// remove item and finish if it was the last
		adapter.remove(item);
		if (adapter.getItemCount() == 0) {
			supportFinishAfterTransition();
		}
	}

	private void respondToInvitation(final ForumInvitationItem item,
			final boolean accept) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Forum f = item.getForum();
					for (Contact c : item.getContacts()) {
						forumSharingManager.respondToInvitation(f, c, accept);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}
