package org.briarproject.android.privategroup.invitation;

import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.sharing.InvitationControllerImpl;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.GroupInvitationRequestReceivedEvent;
import org.briarproject.api.event.GroupInvitationResponseReceivedEvent;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.privategroup.invitation.GroupInvitationItem;
import org.briarproject.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.api.sync.ClientId;

import java.util.Collection;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.privategroup.PrivateGroupManager.CLIENT_ID;

public class GroupInvitationControllerImpl
		extends InvitationControllerImpl<GroupInvitationItem>
		implements GroupInvitationController {

	private final PrivateGroupManager privateGroupManager;
	private final GroupInvitationManager groupInvitationManager;

	@Inject
	GroupInvitationControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, EventBus eventBus,
			PrivateGroupManager privateGroupManager,
			GroupInvitationManager groupInvitationManager) {
		super(dbExecutor, lifecycleManager, eventBus);
		this.privateGroupManager = privateGroupManager;
		this.groupInvitationManager = groupInvitationManager;
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof GroupInvitationRequestReceivedEvent) {
			LOG.info("Group invitation request received, reloading");
			listener.loadInvitations(false);
		} else if (e instanceof GroupInvitationResponseReceivedEvent) {
			LOG.info("Group invitation response received, reloading");
			listener.loadInvitations(false);
		}
	}

	@Override
	protected ClientId getShareableClientId() {
		return CLIENT_ID;
	}

	@Override
	protected Collection<GroupInvitationItem> getInvitations()
			throws DbException {
		return groupInvitationManager.getInvitations();
	}

	@Override
	public void respondToInvitation(final GroupInvitationItem item,
			final boolean accept,
			final ResultExceptionHandler<Void, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					PrivateGroup g = item.getShareable();
					ContactId c = item.getCreator().getId();
					groupInvitationManager.respondToInvitation(c, g, accept);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

}
