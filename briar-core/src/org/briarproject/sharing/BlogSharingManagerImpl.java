package org.briarproject.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.blogs.BlogInvitationRequest;
import org.briarproject.api.blogs.BlogInvitationResponse;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogManager.RemoveBlogHook;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.blogs.BlogSharingMessage.BlogInvitation;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.BlogInvitationReceivedEvent;
import org.briarproject.api.event.BlogInvitationResponseReceivedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.security.SecureRandom;

import javax.inject.Inject;

import static org.briarproject.api.blogs.BlogConstants.BLOG_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.BLOG_DESC;
import static org.briarproject.api.blogs.BlogConstants.BLOG_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.BLOG_TITLE;

class BlogSharingManagerImpl extends
		SharingManagerImpl<Blog, BlogInvitation, BlogInviteeSessionState, BlogSharerSessionState, BlogInvitationReceivedEvent, BlogInvitationResponseReceivedEvent>
		implements BlogSharingManager, RemoveBlogHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"bee438b5de0b3a685badc4e49d76e72d"
					+ "21e01c4b569a775112756bdae267a028"));

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
			PrivateGroupFactory privateGroupFactory, SecureRandom random) {

		super(db, messageQueueManager, clientHelper, metadataParser,
				metadataEncoder, random, privateGroupFactory, clock);

		this.blogManager = blogManager;
		sFactory = new SFactory(authorFactory, blogFactory, blogManager);
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
	public boolean canBeShared(GroupId g, Contact c) throws DbException {
		Blog b = blogManager.getPersonalBlog(c.getAuthor());
		if (b.getId().equals(g)) return false;
		return super.canBeShared(g, c);
	}

	@Override
	protected InvitationMessage createInvitationRequest(MessageId id,
			BlogInvitation msg, ContactId contactId, boolean available,
			long time, boolean local, boolean sent, boolean seen,
			boolean read) {
		return new BlogInvitationRequest(id, msg.getSessionId(), contactId,
				msg.getBlogAuthorName(), msg.getMessage(), available, time,
				local, sent, seen, read);
	}

	@Override
	protected InvitationMessage createInvitationResponse(MessageId id,
			SessionId sessionId, ContactId contactId, boolean accept, long time,
			boolean local, boolean sent, boolean seen, boolean read) {
		return new BlogInvitationResponse(id, sessionId, contactId, accept,
				time, local, sent, seen, read);
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
	protected InvitationReceivedEventFactory<BlogInviteeSessionState, BlogInvitationReceivedEvent> getIRFactory() {
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

		SFactory(AuthorFactory authorFactory, BlogFactory BlogFactory,
				BlogManager BlogManager) {
			this.authorFactory = authorFactory;
			this.blogFactory = BlogFactory;
			this.blogManager = BlogManager;
		}

		@Override
		public BdfList encode(Blog f) {
			return BdfList.of(f.getName(), f.getDescription(),
					BdfList.of(f.getAuthor().getName(),
							f.getAuthor().getPublicKey()));
		}

		@Override
		public Blog get(Transaction txn, GroupId groupId)
				throws DbException {
			return blogManager.getBlog(txn, groupId);
		}

		@Override
		public Blog parse(BdfList shareable) throws FormatException {
			Author author = authorFactory
					.createAuthor(shareable.getList(2).getString(0),
							shareable.getList(2).getRaw(1));
			return blogFactory
					.createBlog(shareable.getString(0), shareable.getString(1),
							author);
		}

		@Override
		public Blog parse(BlogInvitation msg) {
			Author author = authorFactory.createAuthor(msg.getBlogAuthorName(),
					msg.getBlogPublicKey());
			return blogFactory
					.createBlog(msg.getBlogTitle(), msg.getBlogDesc(), author);
		}

		public Blog parse(BlogInviteeSessionState state) {
			Author author = authorFactory
					.createAuthor(state.getBlogAuthorName(),
							state.getBlogPublicKey());
			return blogFactory
					.createBlog(state.getBlogTitle(), state.getBlogDesc(),
							author);
		}

		@Override
		public Blog parse(BlogSharerSessionState state) {
			Author author = authorFactory
					.createAuthor(state.getBlogAuthorName(),
							state.getBlogPublicKey());
			return blogFactory
					.createBlog(state.getBlogTitle(), state.getBlogDesc(),
							author);
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
		public BlogInvitation build(BlogSharerSessionState localState) {
			return new BlogInvitation(localState.getGroupId(),
					localState.getSessionId(), localState.getBlogTitle(),
					localState.getBlogDesc(), localState.getBlogAuthorName(),
					localState.getBlogPublicKey(), localState.getMessage());
		}
	}

	private static class ISFactory implements
			InviteeSessionStateFactory<Blog, BlogInviteeSessionState> {
		@Override
		public BlogInviteeSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				InviteeSessionState.State state, ContactId contactId,
				GroupId blogId, BdfDictionary d) throws FormatException {
			String blogTitle = d.getString(BLOG_TITLE);
			String blogDesc = d.getString(BLOG_DESC);
			String blogAuthorName = d.getString(BLOG_AUTHOR_NAME);
			byte[] blogPublicKey = d.getRaw(BLOG_PUBLIC_KEY);
			return new BlogInviteeSessionState(sessionId, storageId,
					groupId, state, contactId, blogId, blogTitle, blogDesc,
					blogAuthorName, blogPublicKey);
		}

		@Override
		public BlogInviteeSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				InviteeSessionState.State state, ContactId contactId,
				Blog blog) {
			return new BlogInviteeSessionState(sessionId, storageId,
					groupId, state, contactId, blog.getId(), blog.getName(),
					blog.getDescription(), blog.getAuthor().getName(),
					blog.getAuthor().getPublicKey());
		}
	}

	private static class SSFactory implements
			SharerSessionStateFactory<Blog, BlogSharerSessionState> {
		@Override
		public BlogSharerSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				SharerSessionState.State state, ContactId contactId,
				GroupId blogId, BdfDictionary d) throws FormatException {
			String blogTitle = d.getString(BLOG_TITLE);
			String blogDesc = d.getString(BLOG_DESC);
			String blogAuthorName = d.getString(BLOG_AUTHOR_NAME);
			byte[] blogPublicKey = d.getRaw(BLOG_PUBLIC_KEY);
			return new BlogSharerSessionState(sessionId, storageId,
					groupId, state, contactId, blogId, blogTitle, blogDesc,
					blogAuthorName, blogPublicKey);
		}

		@Override
		public BlogSharerSessionState build(SessionId sessionId,
				MessageId storageId, GroupId groupId,
				SharerSessionState.State state, ContactId contactId,
				Blog blog) {
			return new BlogSharerSessionState(sessionId, storageId,
					groupId, state, contactId, blog.getId(), blog.getName(),
					blog.getDescription(), blog.getAuthor().getName(),
					blog.getAuthor().getPublicKey());
		}
	}

	private static class IRFactory implements
			InvitationReceivedEventFactory<BlogInviteeSessionState, BlogInvitationReceivedEvent> {

		private final SFactory sFactory;

		IRFactory(SFactory sFactory) {
			this.sFactory = sFactory;
		}

		@Override
		public BlogInvitationReceivedEvent build(
				BlogInviteeSessionState localState) {
			Blog blog = sFactory.parse(localState);
			ContactId contactId = localState.getContactId();
			return new BlogInvitationReceivedEvent(blog, contactId);
		}
	}

	private static class IRRFactory implements
			InvitationResponseReceivedEventFactory<BlogSharerSessionState, BlogInvitationResponseReceivedEvent> {
		@Override
		public BlogInvitationResponseReceivedEvent build(
				BlogSharerSessionState localState) {
			String title = localState.getBlogTitle();
			ContactId c = localState.getContactId();
			return new BlogInvitationResponseReceivedEvent(title, c);
		}
	}
}
