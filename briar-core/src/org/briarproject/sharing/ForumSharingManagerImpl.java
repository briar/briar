package org.briarproject.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
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
import org.briarproject.api.forum.ForumInvitationMessage;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.forum.ForumSharingMessage.ForumInvitation;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.security.SecureRandom;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT;

class ForumSharingManagerImpl extends
		SharingManagerImpl<Forum, ForumInvitation, ForumInvitationMessage, ForumInviteeSessionState, ForumSharerSessionState, ForumInvitationReceivedEvent, ForumInvitationResponseReceivedEvent>
		implements ForumSharingManager, ForumManager.RemoveForumHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"cd11a5d04dccd9e2931d6fc3df456313"
					+ "63bb3e9d9d0e9405fccdb051f41f5449"));

	private final SFactory sFactory;
	private final IFactory iFactory;
	private final IMFactory imFactory;
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
			PrivateGroupFactory privateGroupFactory,
			SecureRandom random) {
		super(db, messageQueueManager, clientHelper, metadataParser,
				metadataEncoder, random, privateGroupFactory, clock);
		sFactory = new SFactory(forumFactory, forumManager);
		iFactory = new IFactory();
		imFactory = new IMFactory();
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
	protected ShareableFactory<Forum, ForumInvitation, ForumInviteeSessionState, ForumSharerSessionState> getSFactory() {
		return sFactory;
	}

	@Override
	protected InvitationFactory<ForumInvitation, ForumSharerSessionState> getIFactory() {
		return iFactory;
	}

	@Override
	protected InvitationMessageFactory<ForumInvitation, ForumInvitationMessage> getIMFactory() {
		return imFactory;
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

	static class SFactory implements
			ShareableFactory<Forum, ForumInvitation, ForumInviteeSessionState, ForumSharerSessionState> {

		private final ForumFactory forumFactory;
		private final ForumManager forumManager;

		SFactory(ForumFactory forumFactory, ForumManager forumManager) {
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

	static class IFactory implements
			InvitationFactory<ForumInvitation, ForumSharerSessionState> {
		@Override
		public ForumInvitation build(GroupId groupId, BdfDictionary d)
				throws FormatException {
			return ForumInvitation.from(groupId, d);
		}

		@Override
		public ForumInvitation build(ForumSharerSessionState localState) {
			return new ForumInvitation(localState.getGroupId(),
					localState.getSessionId(), localState.getForumName(),
					localState.getForumSalt(), localState.getMessage());
		}
	}

	static class IMFactory implements
			InvitationMessageFactory<ForumInvitation, ForumInvitationMessage> {
		@Override
		public ForumInvitationMessage build(MessageId id,
				ForumInvitation msg, ContactId contactId,
				boolean available, long time, boolean local, boolean sent,
				boolean seen, boolean read) {
			return new ForumInvitationMessage(id, msg.getSessionId(), contactId,
					msg.getForumName(), msg.getMessage(), available, time,
					local,
					sent, seen, read);
		}
	}

	static class ISFactory implements
			InviteeSessionStateFactory<Forum, ForumInviteeSessionState> {
		@Override
		public ForumInviteeSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				InviteeSessionState.State state, ContactId contactId,
				GroupId forumId, BdfDictionary d) throws FormatException {
			String forumName = d.getString(FORUM_NAME);
			byte[] forumSalt = d.getRaw(FORUM_SALT);
			return new ForumInviteeSessionState(sessionId, storageId,
					groupId, state, contactId, forumId, forumName, forumSalt);
		}

		@Override
		public ForumInviteeSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				InviteeSessionState.State state, ContactId contactId,
				Forum forum) {
			return new ForumInviteeSessionState(sessionId, storageId,
					groupId, state, contactId, forum.getId(), forum.getName(),
					forum.getSalt());
		}
	}

	static class SSFactory implements
			SharerSessionStateFactory<Forum, ForumSharerSessionState> {
		@Override
		public ForumSharerSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				SharerSessionState.State state, ContactId contactId,
				GroupId forumId, BdfDictionary d) throws FormatException {
			String forumName = d.getString(FORUM_NAME);
			byte[] forumSalt = d.getRaw(FORUM_SALT);
			return new ForumSharerSessionState(sessionId, storageId,
					groupId, state, contactId, forumId, forumName, forumSalt);
		}

		@Override
		public ForumSharerSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				SharerSessionState.State state, ContactId contactId,
				Forum forum) {
			return new ForumSharerSessionState(sessionId, storageId,
					groupId, state, contactId, forum.getId(), forum.getName(),
					forum.getSalt());
		}
	}

	static class IRFactory implements
			InvitationReceivedEventFactory<ForumInviteeSessionState, ForumInvitationReceivedEvent> {

		private final SFactory sFactory;

		IRFactory(SFactory sFactory) {
			this.sFactory = sFactory;
		}

		@Override
		public ForumInvitationReceivedEvent build(
				ForumInviteeSessionState localState) {
			return new ForumInvitationReceivedEvent(localState.getContactId(),
					localState.getStorageId(), sFactory.parse(localState));
		}
	}

	static class IRRFactory implements
			InvitationResponseReceivedEventFactory<ForumSharerSessionState, ForumInvitationResponseReceivedEvent> {
		@Override
		public ForumInvitationResponseReceivedEvent build(
				ForumSharerSessionState localState) {
			String name = localState.getForumName();
			ContactId c = localState.getContactId();
			return new ForumInvitationResponseReceivedEvent(name, c);
		}
	}
}
