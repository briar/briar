package org.briarproject.android.privategroup.invitation;

import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.android.sharing.InvitationsControllerImpl;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.GroupInvitationReceivedEvent;
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

public class InvitationsGroupControllerImpl
		extends InvitationsControllerImpl<GroupInvitationItem>
		implements InvitationsGroupController {

	private final PrivateGroupManager privateGroupManager;
	private final GroupInvitationManager groupInvitationManager;

	@Inject
	InvitationsGroupControllerImpl(@DatabaseExecutor Executor dbExecutor,
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

		if (e instanceof GroupInvitationReceivedEvent) {
			LOG.info("Group invitation received, reloading");
			listener.loadInvitations(false);
		}
	}

	@Override
	protected ClientId getClientId() {
		return privateGroupManager.getClientId();
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
					PrivateGroup g = (PrivateGroup) item.getShareable();
					Contact c = item.getCreator();
					groupInvitationManager.respondToInvitation(g, c, accept);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

}
