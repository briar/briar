package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.blog.BlogInvitationRequest;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogManager.RemoveBlogHook;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.blog.BlogSharingMessage.BlogInvitation;
import org.briarproject.briar.api.blog.event.BlogInvitationRequestReceivedEvent;
import org.briarproject.briar.api.blog.event.BlogInvitationResponseReceivedEvent;
import org.briarproject.briar.api.client.MessageQueueManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.sharing.InvitationMessage;

import java.security.SecureRandom;
import java.util.Collection;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.blog.BlogConstants.BLOG_AUTHOR_NAME;
import static org.briarproject.briar.api.blog.BlogConstants.BLOG_PUBLIC_KEY;
import static org.briarproject.briar.api.sharing.SharingConstants.INVITATION_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.RESPONSE_ID;

@Immutable
@NotNullByDefault
class BlogSharingManagerImpl extends
		SharingManagerImpl<Blog, BlogInvitation, BlogInviteeSessionState, BlogSharerSessionState, BlogInvitationRequestReceivedEvent, BlogInvitationResponseReceivedEvent>
		implements BlogSharingManager, RemoveBlogHook {

	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final BlogManager blogManager;

	private final SFactory sFactory;
	private final IFactory iFactory;
	private final ISFactory isFactory;
	private final SSFactory ssFactory;
	private final IRFactory irFactory;
	private final IRRFactory irrFactory;

	@Inject
	BlogSharingManagerImpl(AuthorFactory authorFactory, BlogFactory blogFactory,
			BlogManager blogManager, ClientHelper clientHelper, Clock clock,
			DatabaseComponent db, MessageQueueManager messageQueueManager,
			MetadataEncoder metadataEncoder, MetadataParser metadataParser,
			ContactGroupFactory contactGroupFactory, SecureRandom random,
			ContactManager contactManager, IdentityManager identityManager,
			MessageTracker messageTracker) {

		super(db, messageQueueManager, clientHelper, metadataParser,
				metadataEncoder, random, contactGroupFactory, messageTracker,
				clock);

		this.blogManager = blogManager;
		this.contactManager = contactManager;
		this.identityManager = identityManager;
		sFactory = new SFactory(authorFactory, blogFactory, blogManager);
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
	protected boolean canBeShared(Transaction txn, GroupId g, Contact c)
			throws DbException {

		// check if g is our personal blog
		LocalAuthor author = identityManager.getLocalAuthor(txn);
		Blog b = blogManager.getPersonalBlog(author);
		if (b.getId().equals(g)) return false;

		// check if g is c's personal blog
		b = blogManager.getPersonalBlog(c.getAuthor());
		if (b.getId().equals(g)) return false;

		return super.canBeShared(txn, g, c);
	}

	@Override
	public Collection<Contact> getSharedWith(GroupId g) throws DbException {
		Blog blog = blogManager.getBlog(g);
		LocalAuthor author = identityManager.getLocalAuthor();
		if (blog.getAuthor().equals(author)) {
			// This is our personal blog. It is shared with all our contacts
			return contactManager.getActiveContacts();
		} else {
			// This is someone else's blog. Look up who it is shared with
			Collection<Contact> shared = super.getSharedWith(g);
			// If the blog author is our contact, also add her to the list
			boolean isContact = contactManager
					.contactExists(blog.getAuthor().getId(), author.getId());
			if (isContact) {
				shared.add(contactManager
						.getContact(blog.getAuthor().getId(), author.getId()));
			}
			return shared;
		}
	}

	@Override
	protected InvitationMessage createInvitationRequest(MessageId id,
			BlogInvitation msg, ContactId contactId, GroupId blogId,
			boolean available, boolean canBeOpened, long time, boolean local,
			boolean sent, boolean seen, boolean read) {

		return new BlogInvitationRequest(id, msg.getSessionId(),
				msg.getGroupId(), contactId, msg.getBlogAuthorName(),
				msg.getMessage(), blogId, available, canBeOpened, time, local,
				sent, seen, read);
	}

	@Override
	protected InvitationMessage createInvitationResponse(MessageId id,
			SessionId sessionId, GroupId groupId, ContactId contactId,
			GroupId blogId, boolean accept, long time, boolean local,
			boolean sent, boolean seen, boolean read) {
		return new BlogInvitationResponse(id, sessionId, groupId, contactId,
				blogId, accept, time, local, sent, seen, read);
	}

	@Override
	protected ShareableFactory<Blog, BlogInvitation, BlogInviteeSessionState, BlogSharerSessionState> getSFactory() {
		return sFactory;
	}

	@Override
	protected InvitationFactory<BlogInvitation, BlogSharerSessionState> getIFactory() {
		return iFactory;
	}

	@Override
	protected InviteeSessionStateFactory<Blog, BlogInviteeSessionState> getISFactory() {
		return isFactory;
	}

	@Override
	protected SharerSessionStateFactory<Blog, BlogSharerSessionState> getSSFactory() {
		return ssFactory;
	}

	@Override
	protected InvitationReceivedEventFactory<BlogInviteeSessionState, BlogInvitationRequestReceivedEvent> getIRFactory() {
		return irFactory;
	}

	@Override
	protected InvitationResponseReceivedEventFactory<BlogSharerSessionState, BlogInvitationResponseReceivedEvent> getIRRFactory() {
		return irrFactory;
	}

	@Override
	public void removingBlog(Transaction txn, Blog b) throws DbException {
		removingShareable(txn, b);
	}

	private static class SFactory implements
			ShareableFactory<Blog, BlogInvitation, BlogInviteeSessionState, BlogSharerSessionState> {

		private final AuthorFactory authorFactory;
		private final BlogFactory blogFactory;
		private final BlogManager blogManager;

		private SFactory(AuthorFactory authorFactory, BlogFactory BlogFactory,
				BlogManager BlogManager) {
			this.authorFactory = authorFactory;
			this.blogFactory = BlogFactory;
			this.blogManager = BlogManager;
		}

		@Override
		public BdfList encode(Blog f) {
			return BdfList.of(
					BdfList.of(
							f.getAuthor().getName(),
							f.getAuthor().getPublicKey()
					)
			);
		}

		@Override
		public Blog get(Transaction txn, GroupId groupId)
				throws DbException {
			return blogManager.getBlog(txn, groupId);
		}

		@Override
		public Blog parse(BdfList shareable) throws FormatException {
			Author author = authorFactory
					.createAuthor(shareable.getList(0).getString(0),
							shareable.getList(0).getRaw(1));
			return blogFactory.createBlog(author);
		}

		@Override
		public Blog parse(BlogInvitation msg) {
			Author author = authorFactory.createAuthor(msg.getBlogAuthorName(),
					msg.getBlogPublicKey());
			return blogFactory.createBlog(author);
		}

		@Override
		public Blog parse(BlogInviteeSessionState state) {
			Author author = authorFactory
					.createAuthor(state.getBlogAuthorName(),
							state.getBlogPublicKey());
			return blogFactory.createBlog(author);
		}

		@Override
		public Blog parse(BlogSharerSessionState state) {
			Author author = authorFactory
					.createAuthor(state.getBlogAuthorName(),
							state.getBlogPublicKey());
			return blogFactory.createBlog(author);
		}
	}

	private static class IFactory implements
			InvitationFactory<BlogInvitation, BlogSharerSessionState> {
		@Override
		public BlogInvitation build(GroupId groupId, BdfDictionary d)
				throws FormatException {
			return BlogInvitation.from(groupId, d);
		}

		@Override
		public BlogInvitation build(BlogSharerSessionState localState,
				long time) {
			return new BlogInvitation(localState.getContactGroupId(),
					localState.getSessionId(), localState.getBlogAuthorName(),
					localState.getBlogPublicKey(), time,
					localState.getMessage());
		}
	}

	private static class ISFactory implements
			InviteeSessionStateFactory<Blog, BlogInviteeSessionState> {
		@Override
		public BlogInviteeSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				InviteeSessionState.State state, ContactId contactId,
				GroupId blogId, BdfDictionary d) throws FormatException {
			String blogAuthorName = d.getString(BLOG_AUTHOR_NAME);
			byte[] blogPublicKey = d.getRaw(BLOG_PUBLIC_KEY);
			MessageId invitationId = new MessageId(d.getRaw(INVITATION_ID));
			return new BlogInviteeSessionState(sessionId, storageId,
					groupId, state, contactId, blogId, blogAuthorName,
					blogPublicKey, invitationId);
		}

		@Override
		public BlogInviteeSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				InviteeSessionState.State state, ContactId contactId,
				Blog blog, MessageId invitationId) {
			return new BlogInviteeSessionState(sessionId, storageId,
					groupId, state, contactId, blog.getId(),
					blog.getAuthor().getName(), blog.getAuthor().getPublicKey(),
					invitationId);
		}
	}

	private static class SSFactory implements
			SharerSessionStateFactory<Blog, BlogSharerSessionState> {
		@Override
		public BlogSharerSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				SharerSessionState.State state, ContactId contactId,
				GroupId blogId, BdfDictionary d) throws FormatException {
			String blogAuthorName = d.getString(BLOG_AUTHOR_NAME);
			byte[] blogPublicKey = d.getRaw(BLOG_PUBLIC_KEY);
			MessageId responseId = null;
			byte[] responseIdBytes = d.getOptionalRaw(RESPONSE_ID);
			if (responseIdBytes != null)
				responseId = new MessageId(responseIdBytes);
			return new BlogSharerSessionState(sessionId, storageId,
					groupId, state, contactId, blogId, blogAuthorName,
					blogPublicKey, responseId);
		}

		@Override
		public BlogSharerSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				SharerSessionState.State state, ContactId contactId,
				Blog blog) {
			return new BlogSharerSessionState(sessionId, storageId,
					groupId, state, contactId, blog.getId(),
					blog.getAuthor().getName(), blog.getAuthor().getPublicKey(),
					null);
		}
	}

	private static class IRFactory implements
			InvitationReceivedEventFactory<BlogInviteeSessionState, BlogInvitationRequestReceivedEvent> {

		private final SFactory sFactory;

		private IRFactory(SFactory sFactory) {
			this.sFactory = sFactory;
		}

		@Override
		public BlogInvitationRequestReceivedEvent build(
				BlogInviteeSessionState localState, long time,
				@Nullable String msg) {
			Blog blog = sFactory.parse(localState);
			ContactId contactId = localState.getContactId();
			BlogInvitationRequest request =
					new BlogInvitationRequest(localState.getInvitationId(),
							localState.getSessionId(),
							localState.getContactGroupId(), contactId,
							blog.getAuthor().getName(), msg,
							localState.getShareableId(), true, false, time,
							false, false, false, false);
			return new BlogInvitationRequestReceivedEvent(blog, contactId,
					request);
		}
	}

	private static class IRRFactory implements
			InvitationResponseReceivedEventFactory<BlogSharerSessionState, BlogInvitationResponseReceivedEvent> {
		@Override
		public BlogInvitationResponseReceivedEvent build(
				BlogSharerSessionState localState, boolean accept, long time) {
			ContactId c = localState.getContactId();
			MessageId responseId = localState.getResponseId();
			if (responseId == null)
				throw new IllegalStateException("No responseId");
			BlogInvitationResponse response =
					new BlogInvitationResponse(responseId,
							localState.getSessionId(),
							localState.getContactGroupId(),
							localState.getContactId(),
							localState.getShareableId(), accept, time, false,
							false, false, false);
			return new BlogInvitationResponseReceivedEvent(c, response);
		}
	}
}
