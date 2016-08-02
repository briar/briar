package org.briarproject.android.sharing;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.event.BlogInvitationReceivedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.event.InvitationReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.ClientId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.sharing.InvitationAdapter.AvailableForumClickListener;
import static org.briarproject.android.sharing.ShareActivity.BLOG;
import static org.briarproject.android.sharing.ShareActivity.FORUM;
import static org.briarproject.android.sharing.ShareActivity.SHAREABLE;

public class InvitationsActivity extends BriarActivity
		implements EventListener, AvailableForumClickListener {

	private static final Logger LOG =
			Logger.getLogger(InvitationsActivity.class.getName());

	private int shareable;
	private InvitationAdapter adapter;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumManager forumManager;
	@Inject
	protected volatile ForumSharingManager forumSharingManager;
	@Inject
	protected volatile BlogManager blogManager;
	@Inject
	protected volatile BlogSharingManager blogSharingManager;
	@Inject
	protected volatile EventBus eventBus;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_invitations);

		Intent i = getIntent();
		shareable = i.getIntExtra(SHAREABLE, 0);
		if (shareable == 0) throw new IllegalStateException("No Shareable");

		if (shareable == FORUM) {
			adapter = new ForumInvitationAdapter(this, this);
		} else if (shareable == BLOG) {
			adapter = new BlogInvitationAdapter(this, this);
			setTitle(getString(R.string.blogs_sharing_invitations_title));
		} else {
			throw new IllegalArgumentException("Unknown Shareable Type");
		}

		BriarRecyclerView list =
				(BriarRecyclerView) findViewById(R.id.invitationsView);
		if (list != null) {
			list.setLayoutManager(new LinearLayoutManager(this));
			list.setAdapter(adapter);
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
		loadShareables(false);
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
		adapter.clear();
	}

	private void loadShareables(boolean clear) {
		if (shareable == FORUM) {
			loadForums(clear);
		} else if (shareable == BLOG) {
			loadBlogs(clear);
		}
	}

	private void loadForums(final boolean clear) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Collection<InvitationItem> forums = new ArrayList<>();
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
								new InvitationItem(f, subscribed, c));
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayInvitations(forums, clear);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void loadBlogs(final boolean clear) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Collection<InvitationItem> invitations = new ArrayList<>();
					long now = System.currentTimeMillis();
					for (Blog b : blogSharingManager.getInvited()) {
						boolean subscribed;
						try {
							blogManager.getBlog(b.getId());
							subscribed = true;
						} catch (NoSuchGroupException e) {
							subscribed = false;
						}
						Collection<Contact> c =
								blogSharingManager.getSharedBy(b.getId());
						invitations.add(
								new InvitationItem(b, subscribed, c));
					}
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayInvitations(invitations, clear);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayInvitations(final Collection<InvitationItem> invitations,
			final boolean clear) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (invitations.isEmpty()) {
					LOG.info("No more invitations available, finishing");
					finish();
				} else {
					if (clear) adapter.clear();
					adapter.addAll(invitations);
				}
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, reloading");
			loadShareables(true);
		} else if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			ClientId cId = g.getGroup().getClientId();
			if (cId.equals(forumManager.getClientId()) && shareable == FORUM) {
				LOG.info("Forum added, reloading");
				loadShareables(false);
			} else if (cId.equals(blogManager.getClientId()) &&
					shareable == BLOG) {
				LOG.info("Blog added, reloading");
				loadShareables(true);
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			ClientId cId = g.getGroup().getClientId();
			if (cId.equals(forumManager.getClientId()) && shareable == FORUM) {
				LOG.info("Forum removed, reloading");
				loadShareables(true);
			} else if (cId.equals(blogManager.getClientId()) &&
					shareable == BLOG) {
				LOG.info("Blog removed, reloading");
				loadShareables(true);
			}
		} else if (e instanceof InvitationReceivedEvent) {
			if (e instanceof ForumInvitationReceivedEvent &&
					shareable == FORUM) {
				LOG.info("Forum invitation received, reloading");
				loadShareables(false);
			} else if (e instanceof BlogInvitationReceivedEvent &&
					shareable == BLOG) {
				LOG.info("Blog invitation received, reloading");
				loadShareables(false);
			}
		}
	}

	@Override
	public void onItemClick(InvitationItem item, boolean accept) {
		respondToInvitation(item, accept);

		// show toast
		int res;
		if (shareable == FORUM) {
			res = R.string.forum_declined_toast;
			if (accept) res = R.string.forum_joined_toast;
		} else {
			res = R.string.blogs_sharing_declined_toast;
			if (accept) res = R.string.blogs_sharing_joined_toast;
		}
		Toast.makeText(this, res, LENGTH_SHORT).show();

		// remove item and finish if it was the last
		adapter.remove(item);
		if (adapter.getItemCount() == 0) {
			supportFinishAfterTransition();
		}
	}

	private void respondToInvitation(final InvitationItem item,
			final boolean accept) {

		if (shareable == FORUM) {
			respondToForumInvitation(item, accept);
		} else if (shareable == BLOG) {
			respondToBlogInvitation(item, accept);
		}
	}

	private void respondToForumInvitation(final InvitationItem item,
			final boolean accept) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Forum f = (Forum) item.getShareable();
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

	private void respondToBlogInvitation(final InvitationItem item,
			final boolean accept) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Blog b = (Blog) item.getShareable();
					for (Contact c : item.getContacts()) {
						blogSharingManager.respondToInvitation(b, c, accept);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}
