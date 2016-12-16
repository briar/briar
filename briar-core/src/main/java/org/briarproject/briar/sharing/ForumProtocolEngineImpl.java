package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.event.ForumInvitationRequestReceivedEvent;
import org.briarproject.briar.api.forum.event.ForumInvitationResponseReceivedEvent;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class ForumProtocolEngineImpl extends ProtocolEngineImpl<Forum> {

	private final ForumManager forumManager;

	@Inject
	ForumProtocolEngineImpl(DatabaseComponent db,
			ClientHelper clientHelper, MessageEncoder messageEncoder,
			MessageParser<Forum> messageParser, MessageTracker messageTracker,
			Clock clock, ForumManager forumManager) {
		super(db, clientHelper, messageEncoder, messageParser, messageTracker,
				clock);
		this.forumManager = forumManager;
	}

	@Override
	Event getInvitationRequestReceivedEvent(InviteMessage<Forum> m,
			ContactId contactId, boolean available, boolean canBeOpened) {
		ForumInvitationRequest request =
				createInvitationRequest(false, false, true, false, m, contactId,
						available, canBeOpened);
		return new ForumInvitationRequestReceivedEvent(m.getShareable(),
				contactId, request);
	}

	@Override
	Event getInvitationResponseReceivedEvent(AcceptMessage m,
			ContactId contactId) {
		ForumInvitationResponse response =
				createInvitationResponse(m.getId(), m.getContactGroupId(),
						m.getTimestamp(), false, false, true, false,
						m.getShareableId(), contactId, true);
		return new ForumInvitationResponseReceivedEvent(contactId, response);
	}

	@Override
	Event getInvitationResponseReceivedEvent(DeclineMessage m,
			ContactId contactId) {
		ForumInvitationResponse response =
				createInvitationResponse(m.getId(), m.getContactGroupId(),
						m.getTimestamp(), false, false, true, false,
						m.getShareableId(), contactId, true);
		return new ForumInvitationResponseReceivedEvent(contactId, response);
	}

	@Override
	protected void addShareable(Transaction txn, MessageId inviteId)
			throws DbException, FormatException {
		InviteMessage<Forum> invite = getInviteMessage(txn, inviteId);
		forumManager.addForum(txn, invite.getShareable());
	}

	private InviteMessage<Forum> getInviteMessage(Transaction txn, MessageId m)
			throws DbException, FormatException {
		Message message = clientHelper.getMessage(txn, m);
		if (message == null) throw new DbException();
		BdfList body = clientHelper.toList(message);
		return messageParser.parseInviteMessage(message, body);
	}

	@Override
	public ForumInvitationRequest createInvitationRequest(boolean local,
			boolean sent, boolean seen, boolean read, InviteMessage<Forum> m,
			ContactId c, boolean available, boolean canBeOpened) {
		SessionId sessionId = new SessionId(m.getShareableId().getBytes());
		return new ForumInvitationRequest(m.getId(), m.getContactGroupId(),
				m.getTimestamp(), local, sent, seen, read, sessionId,
				m.getShareable(), c, m.getMessage(), available, canBeOpened);
	}

	@Override
	public ForumInvitationResponse createInvitationResponse(MessageId id,
			GroupId contactGroupId, long time, boolean local, boolean sent,
			boolean seen, boolean read, GroupId shareableId,
			ContactId contactId, boolean accept) {
		SessionId sessionId = new SessionId(shareableId.getBytes());
		return new ForumInvitationResponse(id, contactGroupId, time, local,
				sent, seen, read, sessionId, shareableId, contactId, accept);
	}

}
