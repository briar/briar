package org.briarproject.briar.android.privategroup.conversation;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.android.privategroup.conversation.GroupController.GroupListener;
import org.briarproject.briar.android.threaded.ThreadListControllerImpl;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.privategroup.GroupMember;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.JoinMessageHeader;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.event.ContactRelationshipRevealedEvent;
import org.briarproject.briar.api.privategroup.event.GroupDissolvedEvent;
import org.briarproject.briar.api.privategroup.event.GroupInvitationResponseReceivedEvent;
import org.briarproject.briar.api.privategroup.event.GroupMessageAddedEvent;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.lang.Math.max;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class GroupControllerImpl extends
		ThreadListControllerImpl<PrivateGroup, GroupMessageItem, GroupMessageHeader, GroupMessage, GroupListener>
		implements GroupController {

	private static final Logger LOG =
			Logger.getLogger(GroupControllerImpl.class.getName());

	private final PrivateGroupManager privateGroupManager;
	private final GroupMessageFactory groupMessageFactory;

	@Inject
	GroupControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, IdentityManager identityManager,
			@CryptoExecutor Executor cryptoExecutor,
			PrivateGroupManager privateGroupManager,
			GroupMessageFactory groupMessageFactory, EventBus eventBus,
			MessageTracker messageTracker, Clock clock,
			AndroidNotificationManager notificationManager) {
		super(dbExecutor, lifecycleManager, identityManager, cryptoExecutor,
				eventBus, clock, notificationManager, messageTracker);
		this.privateGroupManager = privateGroupManager;
		this.groupMessageFactory = groupMessageFactory;
	}

	@Override
	public void onActivityStart() {
		super.onActivityStart();
		notificationManager.clearGroupMessageNotification(getGroupId());
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);

		if (e instanceof GroupMessageAddedEvent) {
			GroupMessageAddedEvent g = (GroupMessageAddedEvent) e;
			if (!g.isLocal() && g.getGroupId().equals(getGroupId())) {
				LOG.info("Group message received, adding...");
				listener.onItemReceived(buildItem(g.getHeader(), g.getText()));
			}
		} else if (e instanceof ContactRelationshipRevealedEvent) {
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
		} else if (e instanceof GroupDissolvedEvent) {
			GroupDissolvedEvent g = (GroupDissolvedEvent) e;
			if (getGroupId().equals(g.getGroupId())) {
				listener.onGroupDissolved();
			}
		}
	}

	@Override
	protected PrivateGroup loadNamedGroup() throws DbException {
		return privateGroupManager.getPrivateGroup(getGroupId());
	}

	@Override
	protected Collection<GroupMessageHeader> loadHeaders() throws DbException {
		return privateGroupManager.getHeaders(getGroupId());
	}

	@Override
	protected String loadMessageText(GroupMessageHeader header)
			throws DbException {
		if (header instanceof JoinMessageHeader) {
			// will be looked up later
			return "";
		}
		return privateGroupManager.getMessageText(header.getId());
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

	@Override
	public void createAndStoreMessage(String text,
			@Nullable GroupMessageItem parentItem,
			ResultExceptionHandler<GroupMessageItem, DbException> handler) {
		runOnDbThread(() -> {
			try {
				LocalAuthor author = identityManager.getLocalAuthor();
				MessageId parentId = null;
				MessageId previousMsgId =
						privateGroupManager.getPreviousMsgId(getGroupId());
				GroupCount count =
						privateGroupManager.getGroupCount(getGroupId());
				long timestamp = count.getLatestMsgTime();
				if (parentItem != null) parentId = parentItem.getId();
				timestamp = max(clock.currentTimeMillis(), timestamp + 1);
				createMessage(text, timestamp, parentId, author, previousMsgId,
						handler);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	private void createMessage(String text, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author,
			MessageId previousMsgId,
			ResultExceptionHandler<GroupMessageItem, DbException> handler) {
		cryptoExecutor.execute(() -> {
			LOG.info("Creating group message...");
			GroupMessage msg = groupMessageFactory
					.createGroupMessage(getGroupId(), timestamp,
							parentId, author, text, previousMsgId);
			storePost(msg, text, handler);
		});
	}

	@Override
	protected GroupMessageHeader addLocalMessage(GroupMessage message)
			throws DbException {
		return privateGroupManager.addLocalMessage(message);
	}

	@Override
	protected void deleteNamedGroup(PrivateGroup group) throws DbException {
		privateGroupManager.removePrivateGroup(group.getId());
	}

	@Override
	protected GroupMessageItem buildItem(GroupMessageHeader header,
			String text) {
		if (header instanceof JoinMessageHeader) {
			return new JoinMessageItem((JoinMessageHeader) header, text);
		}
		return new GroupMessageItem(header, text);
	}

	@Override
	public void isDissolved(
			ResultExceptionHandler<Boolean, DbException> handler) {
		runOnDbThread(() -> {
			try {
				boolean isDissolved =
						privateGroupManager.isDissolved(getGroupId());
				handler.onResult(isDissolved);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

}
