package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.event.ForumInvitationRequestReceivedEvent;
import org.briarproject.briar.api.forum.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.briar.api.sharing.InvitationRequest;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class ForumProtocolEngineImpl extends ProtocolEngineImpl<Forum> {

	private final ForumManager forumManager;
	private final InvitationFactory<Forum, ForumInvitationResponse>
			invitationFactory;

	@Inject
	ForumProtocolEngineImpl(DatabaseComponent db,
			ClientHelper clientHelper, MessageEncoder messageEncoder,
			MessageParser<Forum> messageParser, MessageTracker messageTracker,
			Clock clock, ForumManager forumManager,
			InvitationFactory<Forum, ForumInvitationResponse> invitationFactory) {
		super(db, clientHelper, messageEncoder, messageParser, messageTracker,
				clock);
		this.forumManager = forumManager;
		this.invitationFactory = invitationFactory;
	}

	@Override
	Event getInvitationRequestReceivedEvent(InviteMessage<Forum> m,
			ContactId contactId, boolean available, boolean canBeOpened) {
		InvitationRequest<Forum> request = invitationFactory
						.createInvitationRequest(false, false, true, false, m,
								contactId, available, canBeOpened);
		return new ForumInvitationRequestReceivedEvent(m.getShareable(),
				contactId, request);
	}

	@Override
	Event getInvitationResponseReceivedEvent(AcceptMessage m,
			ContactId contactId) {
		ForumInvitationResponse response = invitationFactory
				.createInvitationResponse(m.getId(), m.getContactGroupId(),
						m.getTimestamp(), false, false, true, false,
						m.getShareableId(), contactId, true);
		return new ForumInvitationResponseReceivedEvent(contactId, response);
	}

	@Override
	Event getInvitationResponseReceivedEvent(DeclineMessage m,
			ContactId contactId) {
		ForumInvitationResponse response = invitationFactory
				.createInvitationResponse(m.getId(), m.getContactGroupId(),
						m.getTimestamp(), false, false, true, false,
						m.getShareableId(), contactId, true);
		return new ForumInvitationResponseReceivedEvent(contactId, response);
	}

	@Override
	protected ClientId getShareableClientId() {
		return ForumManager.CLIENT_ID;
	}

	@Override
	protected void addShareable(Transaction txn, MessageId inviteId)
			throws DbException, FormatException {
		InviteMessage<Forum> invite =
				messageParser.getInviteMessage(txn, inviteId);
		forumManager.addForum(txn, invite.getShareable());
	}

}
