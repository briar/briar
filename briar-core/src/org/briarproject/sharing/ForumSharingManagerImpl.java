package org.briarproject.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumFactory;
import org.briarproject.api.forum.ForumInvitationRequest;
import org.briarproject.api.forum.ForumInvitationResponse;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.forum.ForumSharingMessage.ForumInvitation;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.security.SecureRandom;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.api.sharing.SharingConstants.INVITATION_ID;

class ForumSharingManagerImpl extends
		SharingManagerImpl<Forum, ForumInvitation, ForumInviteeSessionState, ForumSharerSessionState, ForumInvitationReceivedEvent, ForumInvitationResponseReceivedEvent>
		implements ForumSharingManager, ForumManager.RemoveForumHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"cd11a5d04dccd9e2931d6fc3df456313"
					+ "63bb3e9d9d0e9405fccdb051f41f5449"));

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
			SecureRandom random) {
		super(db, messageQueueManager, clientHelper, metadataParser,
				metadataEncoder, random, contactGroupFactory, clock);

		sFactory = new SFactory(forumFactory, forumManager);
		iFactory = new IFactory();
		isFactory = new ISFactory();
		ssFactory = new SSFactory();
		irFactory = new IRFactory(sFactory);
		irrFactory = new IRRFactory();
	}

	@Override
	public ClientId getClientId() {
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
	protected InvitationReceivedEventFactory<ForumInviteeSessionState, ForumInvitationReceivedEvent> getIRFactory() {
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
			MessageId invitationId = null;
			byte[] invitationIdBytes = d.getOptionalRaw(INVITATION_ID);
			if (invitationIdBytes != null)
				invitationId = new MessageId(invitationIdBytes);
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
			byte[] responseIdBytes = d.getOptionalRaw(INVITATION_ID);
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
			InvitationReceivedEventFactory<ForumInviteeSessionState, ForumInvitationReceivedEvent> {

		private final SFactory sFactory;

		private IRFactory(SFactory sFactory) {
			this.sFactory = sFactory;
		}

		@Override
		public ForumInvitationReceivedEvent build(
				ForumInviteeSessionState localState, long time, String msg) {
			Forum forum = sFactory.parse(localState);
			ContactId contactId = localState.getContactId();
			ForumInvitationRequest request = new ForumInvitationRequest(
					localState.getInvitationId(), localState.getSessionId(),
					localState.getGroupId(), contactId, forum.getName(), msg,
					true, time, false, false, false, false);
			return new ForumInvitationReceivedEvent(forum, contactId, request);
		}
	}

	private static class IRRFactory implements
			InvitationResponseReceivedEventFactory<ForumSharerSessionState, ForumInvitationResponseReceivedEvent> {
		@Override
		public ForumInvitationResponseReceivedEvent build(
				ForumSharerSessionState localState, boolean accept, long time) {
			String name = localState.getForumName();
			ContactId c = localState.getContactId();
			ForumInvitationResponse response = new ForumInvitationResponse(
					localState.getResponseId(), localState.getSessionId(),
					localState.getGroupId(), localState.getContactId(), accept,
					time, false, false, false, false);
			return new ForumInvitationResponseReceivedEvent(name, c, response);
		}
	}
}
