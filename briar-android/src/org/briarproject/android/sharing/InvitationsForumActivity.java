package org.briarproject.android.sharing;

import android.content.Context;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sharing.InvitationItem;
import org.briarproject.api.sync.ClientId;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.sharing.InvitationAdapter.AvailableForumClickListener;

public class InvitationsForumActivity extends InvitationsActivity {

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile ForumManager forumManager;
	@Inject
	protected volatile ForumSharingManager forumSharingManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			ClientId cId = g.getGroup().getClientId();
			if (cId.equals(forumManager.getClientId())) {
				LOG.info("Forum added, reloading");
				loadInvitations(false);
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			ClientId cId = g.getGroup().getClientId();
			if (cId.equals(forumManager.getClientId())) {
				LOG.info("Forum removed, reloading");
				loadInvitations(false);
			}
		} else if (e instanceof ForumInvitationReceivedEvent) {
			LOG.info("Forum invitation received, reloading");
			loadInvitations(false);
		}
	}

	protected InvitationAdapter getAdapter(Context ctx,
			AvailableForumClickListener listener) {
		return new ForumInvitationAdapter(ctx, listener);
	}

	protected void loadInvitations(final boolean clear) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				Collection<InvitationItem> invitations = new ArrayList<>();
				try {
					long now = System.currentTimeMillis();
					invitations.addAll(forumSharingManager.getInvitations());
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
				displayInvitations(invitations, clear);
			}
		});
	}

	protected void respondToInvitation(final InvitationItem item,
			final boolean accept) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Forum f = (Forum) item.getShareable();
					for (Contact c : item.getNewSharers()) {
						forumSharingManager.respondToInvitation(f, c, accept);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	protected int getAcceptRes() {
		return R.string.forum_joined_toast;
	}

	protected int getDeclineRes() {
		return R.string.forum_declined_toast;
	}
}
