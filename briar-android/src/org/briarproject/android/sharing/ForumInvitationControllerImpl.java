package org.briarproject.android.sharing;

import org.briarproject.android.controller.handler.ExceptionHandler;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.SharingInvitationItem;
import org.briarproject.api.sync.ClientId;

import java.util.Collection;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.forum.ForumManager.CLIENT_ID;

@NotNullByDefault
class ForumInvitationControllerImpl
		extends InvitationControllerImpl<SharingInvitationItem>
		implements ForumInvitationController {

	private final ForumSharingManager forumSharingManager;

	@Inject
	ForumInvitationControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, EventBus eventBus,
			ForumSharingManager forumSharingManager) {
		super(dbExecutor, lifecycleManager, eventBus);
		this.forumSharingManager = forumSharingManager;
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof ForumInvitationReceivedEvent) {
			LOG.info("Forum invitation received, reloading");
			listener.loadInvitations(false);
		}
	}

	@Override
	protected ClientId getShareableClientId() {
		return CLIENT_ID;
	}

	@Override
	protected Collection<SharingInvitationItem> getInvitations() throws DbException {
		return forumSharingManager.getInvitations();
	}

	@Override
	public void respondToInvitation(final SharingInvitationItem item,
			final boolean accept,
			final ExceptionHandler<DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Forum f = (Forum) item.getShareable();
					for (Contact c : item.getNewSharers()) {
						// TODO: What happens if a contact has been removed?
						forumSharingManager.respondToInvitation(f, c, accept);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

}
