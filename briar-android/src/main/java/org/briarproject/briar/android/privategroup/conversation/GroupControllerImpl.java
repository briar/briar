package org.briarproject.briar.android.privategroup.conversation;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.android.threaded.ThreadListControllerImpl;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.privategroup.GroupMember;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.event.ContactRelationshipRevealedEvent;
import org.briarproject.briar.api.privategroup.event.GroupInvitationResponseReceivedEvent;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class GroupControllerImpl extends ThreadListControllerImpl<GroupMessageItem>
		implements GroupController {

	private static final Logger LOG =
			getLogger(GroupControllerImpl.class.getName());

	private final PrivateGroupManager privateGroupManager;

	@Inject
	GroupControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			@CryptoExecutor Executor cryptoExecutor,
			PrivateGroupManager privateGroupManager,
			EventBus eventBus, Clock clock,
			AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager, identityManager, cryptoExecutor,
				eventBus, clock, notificationManager);
		this.privateGroupManager = privateGroupManager;
	}

	@Override
	public void onActivityStart() {
		super.onActivityStart();
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		GroupListener listener = (GroupListener) this.listener;

		if (e instanceof ContactRelationshipRevealedEvent) {
			ContactRelationshipRevealedEvent c =
					(ContactRelationshipRevealedEvent) e;
			if (getGroupId().equals(c.getGroupId())) {
				listener.onContactRelationshipRevealed(c.getMemberId(),
						c.getContactId(), c.getVisibility());
			}
		} else if (e instanceof GroupInvitationResponseReceivedEvent) {
			GroupInvitationResponseReceivedEvent g =
					(GroupInvitationResponseReceivedEvent) e;
			GroupInvitationResponse r = g.getMessageHeader();
			if (getGroupId().equals(r.getShareableId()) && r.wasAccepted()) {
				listener.onInvitationAccepted(g.getContactId());
			}
		}
	}

	@Override
	protected void markRead(MessageId id) throws DbException {
		privateGroupManager.setReadFlag(getGroupId(), id, true);
	}

	@Override
	public void loadSharingContacts(
			ResultExceptionHandler<Collection<ContactId>, DbException> handler) {
		runOnDbThread(() -> {
			try {
				Collection<GroupMember> members =
						privateGroupManager.getMembers(getGroupId());
				Collection<ContactId> contactIds = new ArrayList<>();
				for (GroupMember m : members) {
					if (m.getContactId() != null)
						contactIds.add(m.getContactId());
				}
				handler.onResult(contactIds);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

}
