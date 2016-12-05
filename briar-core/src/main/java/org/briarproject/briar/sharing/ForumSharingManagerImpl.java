package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.MessageQueueManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumFactory;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumManager.RemoveForumHook;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.forum.ForumSharingMessage.ForumInvitation;
import org.briarproject.briar.api.forum.event.ForumInvitationRequestReceivedEvent;
import org.briarproject.briar.api.forum.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.briar.api.sharing.InvitationMessage;

import java.security.SecureRandom;

import javax.inject.Inject;

import static org.briarproject.briar.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.briar.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.briar.api.sharing.SharingConstants.INVITATION_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.RESPONSE_ID;

@NotNullByDefault
class ForumSharingManagerImpl extends
		SharingManagerImpl<Forum, ForumInvitation, ForumInviteeSessionState, ForumSharerSessionState, ForumInvitationRequestReceivedEvent, ForumInvitationResponseReceivedEvent>
		implements ForumSharingManager, RemoveForumHook {

	private final SFactory sFactory;
	private final IFactory iFactory;
	private final ISFactory isFactory;
	private final SSFactory ssFactory;
	private final IRFactory irFactory;
	private final IRRFactory irrFactory;

	@Inject
	ForumSharingManagerImpl(ClientHelper clientHelper,
			Clock clock, DatabaseComponent db,
			ForumFactory forumFactory,
			ForumManager forumManager,
			MessageQueueManager messageQueueManager,
			MetadataEncoder metadataEncoder,
			MetadataParser metadataParser,
			ContactGroupFactory contactGroupFactory,
			SecureRandom random, MessageTracker messageTracker) {
		super(db, messageQueueManager, clientHelper, metadataParser,
				metadataEncoder, random, contactGroupFactory, messageTracker,
				clock);

		sFactory = new SFactory(forumFactory, forumManager);
		iFactory = new IFactory();
		isFactory = new ISFactory();
		ssFactory = new SSFactory();
		irFactory = new IRFactory(sFactory);
		irrFactory = new IRRFactory();
	}

	@Override
	protected ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	protected InvitationMessage createInvitationRequest(MessageId id,
			ForumInvitation msg, ContactId contactId, boolean available,
			long time, boolean local, boolean sent, boolean seen,
			boolean read) {
		return new ForumInvitationRequest(id, msg.getSessionId(),
				msg.getGroupId(), contactId, msg.getForumName(),
				msg.getMessage(), available, time, local, sent, seen, read);
	}

	@Override
	protected InvitationMessage createInvitationResponse(MessageId id,
			SessionId sessionId, GroupId groupId, ContactId contactId,
			boolean accept, long time, boolean local, boolean sent,
			boolean seen, boolean read) {
		return new ForumInvitationResponse(id, sessionId, groupId, contactId,
				accept, time, local, sent, seen, read);
	}

	@Override
	protected ShareableFactory<Forum, ForumInvitation, ForumInviteeSessionState, ForumSharerSessionState> getSFactory() {
		return sFactory;
	}

	@Override
	protected InvitationFactory<ForumInvitation, ForumSharerSessionState> getIFactory() {
		return iFactory;
	}

	@Override
	protected InviteeSessionStateFactory<Forum, ForumInviteeSessionState> getISFactory() {
		return isFactory;
	}

	@Override
	protected SharerSessionStateFactory<Forum, ForumSharerSessionState> getSSFactory() {
		return ssFactory;
	}

	@Override
	protected InvitationReceivedEventFactory<ForumInviteeSessionState, ForumInvitationRequestReceivedEvent> getIRFactory() {
		return irFactory;
	}

	@Override
	protected InvitationResponseReceivedEventFactory<ForumSharerSessionState, ForumInvitationResponseReceivedEvent> getIRRFactory() {
		return irrFactory;
	}

	@Override
	public void removingForum(Transaction txn, Forum f) throws DbException {
		removingShareable(txn, f);
	}

	private static class SFactory implements
			ShareableFactory<Forum, ForumInvitation, ForumInviteeSessionState, ForumSharerSessionState> {

		private final ForumFactory forumFactory;
		private final ForumManager forumManager;

		private SFactory(ForumFactory forumFactory, ForumManager forumManager) {
			this.forumFactory = forumFactory;
			this.forumManager = forumManager;
		}

		@Override
		public BdfList encode(Forum f) {
			return BdfList.of(f.getName(), f.getSalt());
		}

		@Override
		public Forum get(Transaction txn, GroupId groupId)
				throws DbException {
			return forumManager.getForum(txn, groupId);
		}

		@Override
		public Forum parse(BdfList shareable) throws FormatException {
			return forumFactory
					.createForum(shareable.getString(0), shareable.getRaw(1));
		}

		@Override
		public Forum parse(ForumInvitation msg) {
			return forumFactory
					.createForum(msg.getForumName(), msg.getForumSalt());
		}

		@Override
		public Forum parse(ForumInviteeSessionState state) {
			return forumFactory
					.createForum(state.getForumName(), state.getForumSalt());
		}

		@Override
		public Forum parse(ForumSharerSessionState state) {
			return forumFactory
					.createForum(state.getForumName(), state.getForumSalt());
		}
	}

	private static class IFactory implements
			InvitationFactory<ForumInvitation, ForumSharerSessionState> {
		@Override
		public ForumInvitation build(GroupId groupId, BdfDictionary d)
				throws FormatException {
			return ForumInvitation.from(groupId, d);
		}

		@Override
		public ForumInvitation build(ForumSharerSessionState localState,
				long time) {
			return new ForumInvitation(localState.getGroupId(),
					localState.getSessionId(), localState.getForumName(),
					localState.getForumSalt(), time, localState.getMessage());
		}
	}

	private static class ISFactory implements
			InviteeSessionStateFactory<Forum, ForumInviteeSessionState> {
		@Override
		public ForumInviteeSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				InviteeSessionState.State state, ContactId contactId,
				GroupId forumId, BdfDictionary d) throws FormatException {
			String forumName = d.getString(FORUM_NAME);
			byte[] forumSalt = d.getRaw(FORUM_SALT);
			MessageId invitationId = new MessageId(d.getRaw(INVITATION_ID));
			return new ForumInviteeSessionState(sessionId, storageId,
					groupId, state, contactId, forumId, forumName, forumSalt,
					invitationId);
		}

		@Override
		public ForumInviteeSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				InviteeSessionState.State state, ContactId contactId,
				Forum forum, MessageId invitationId) {
			return new ForumInviteeSessionState(sessionId, storageId,
					groupId, state, contactId, forum.getId(), forum.getName(),
					forum.getSalt(), invitationId);
		}
	}

	private static class SSFactory implements
			SharerSessionStateFactory<Forum, ForumSharerSessionState> {
		@Override
		public ForumSharerSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				SharerSessionState.State state, ContactId contactId,
				GroupId forumId, BdfDictionary d) throws FormatException {
			String forumName = d.getString(FORUM_NAME);
			byte[] forumSalt = d.getRaw(FORUM_SALT);
			MessageId responseId = null;
			byte[] responseIdBytes = d.getOptionalRaw(RESPONSE_ID);
			if (responseIdBytes != null)
				responseId = new MessageId(responseIdBytes);
			return new ForumSharerSessionState(sessionId, storageId,
					groupId, state, contactId, forumId, forumName, forumSalt,
					responseId);
		}

		@Override
		public ForumSharerSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				SharerSessionState.State state, ContactId contactId,
				Forum forum) {
			return new ForumSharerSessionState(sessionId, storageId,
					groupId, state, contactId, forum.getId(), forum.getName(),
					forum.getSalt(), null);
		}
	}

	private static class IRFactory implements
			InvitationReceivedEventFactory<ForumInviteeSessionState, ForumInvitationRequestReceivedEvent> {

		private final SFactory sFactory;

		private IRFactory(SFactory sFactory) {
			this.sFactory = sFactory;
		}

		@Override
		public ForumInvitationRequestReceivedEvent build(
				ForumInviteeSessionState localState, long time, String msg) {
			Forum forum = sFactory.parse(localState);
			ContactId contactId = localState.getContactId();
			ForumInvitationRequest request = new ForumInvitationRequest(
					localState.getInvitationId(), localState.getSessionId(),
					localState.getGroupId(), contactId, forum.getName(), msg,
					true, time, false, false, false, false);
			return new ForumInvitationRequestReceivedEvent(forum, contactId,
					request);
		}
	}

	private static class IRRFactory implements
			InvitationResponseReceivedEventFactory<ForumSharerSessionState, ForumInvitationResponseReceivedEvent> {
		@Override
		public ForumInvitationResponseReceivedEvent build(
				ForumSharerSessionState localState, boolean accept, long time) {
			String name = localState.getForumName();
			ContactId c = localState.getContactId();
			MessageId responseId = localState.getResponseId();
			if (responseId == null)
				throw new IllegalStateException("No responseId");
			ForumInvitationResponse response = new ForumInvitationResponse(
					responseId, localState.getSessionId(),
					localState.getShareableId(), localState.getContactId(),
					accept, time, false, false, false, false);
			return new ForumInvitationResponseReceivedEvent(name, c, response);
		}
	}
}
