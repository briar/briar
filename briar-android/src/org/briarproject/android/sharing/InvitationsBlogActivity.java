package org.briarproject.android.sharing;

import android.content.Context;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.BlogInvitationReceivedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.sharing.InvitationItem;
import org.briarproject.api.sync.ClientId;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.sharing.InvitationAdapter.AvailableForumClickListener;

public class InvitationsBlogActivity extends InvitationsActivity {

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile BlogManager blogManager;
	@Inject
	protected volatile BlogSharingManager blogSharingManager;

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
			if (cId.equals(blogManager.getClientId())) {
				LOG.info("Blog added, reloading");
				loadInvitations(false);
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			ClientId cId = g.getGroup().getClientId();
			if (cId.equals(blogManager.getClientId())) {
				LOG.info("Blog removed, reloading");
				loadInvitations(false);
			}
		} else if (e instanceof BlogInvitationReceivedEvent) {
			LOG.info("Blog invitation received, reloading");
			loadInvitations(false);
		}
	}

	protected InvitationAdapter getAdapter(Context ctx,
			AvailableForumClickListener listener) {
		return new BlogInvitationAdapter(ctx, listener);
	}

	protected void loadInvitations(final boolean clear) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				Collection<InvitationItem> invitations = new ArrayList<>();
				try {
					long now = System.currentTimeMillis();
					invitations.addAll(blogSharingManager.getInvitations());
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
					Blog b = (Blog) item.getShareable();
					for (Contact c : item.getNewSharers()) {
						blogSharingManager.respondToInvitation(b, c, accept);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	protected int getAcceptRes() {
		return R.string.blogs_sharing_joined_toast;
	}

	protected int getDeclineRes() {
		return R.string.blogs_sharing_declined_toast;
	}
}
