package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.SessionId;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.briar.api.blog.BlogSharingManager.CLIENT_ID;
import static org.briarproject.briar.api.blog.BlogSharingManager.CLIENT_VERSION;
import static org.briarproject.briar.sharing.SharingConstants.GROUP_KEY_CONTACT_ID;

public class BlogSharingManagerImplTest extends BrambleMockTestCase {

	private final Mockery context = new Mockery();
	private final BlogSharingManagerImpl blogSharingManager;
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final SessionEncoder sessionEncoder =
			context.mock(SessionEncoder.class);
	private final SessionParser sessionParser =
			context.mock(SessionParser.class);
	private final ContactGroupFactory contactGroupFactory =
			context.mock(ContactGroupFactory.class);
	private final BlogManager blogManager = context.mock(BlogManager.class);

	private final AuthorId localAuthorId = new AuthorId(getRandomId());
	private final ContactId contactId = new ContactId(0);
	private final AuthorId authorId = new AuthorId(getRandomId());
	private final Author author = new Author(authorId, "Author",
			getRandomBytes(MAX_PUBLIC_KEY_LENGTH));
	private final Contact contact =
			new Contact(contactId, author, localAuthorId, true, true);
	private final Collection<Contact> contacts =
			Collections.singletonList(contact);
	private final Group contactGroup =
			new Group(new GroupId(getRandomId()), CLIENT_ID,
					getRandomBytes(42));
	private final Group blogGroup =
			new Group(new GroupId(getRandomId()), BlogManager.CLIENT_ID,
					getRandomBytes(42));
	private final Blog blog = new Blog(blogGroup, author, false);
	@SuppressWarnings("unchecked")
	private final ProtocolEngine<Blog> engine =
			context.mock(ProtocolEngine.class);

	@SuppressWarnings("unchecked")
	public BlogSharingManagerImplTest() {
		MetadataParser metadataParser = context.mock(MetadataParser.class);
		MessageTracker messageTracker = context.mock(MessageTracker.class);
		MessageParser<Blog> messageParser = context.mock(MessageParser.class);
		InvitationFactory<Blog, BlogInvitationResponse> invitationFactory =
				context.mock(InvitationFactory.class);
		blogSharingManager =
				new BlogSharingManagerImpl(db, clientHelper, metadataParser,
						messageParser, sessionEncoder, sessionParser,
						messageTracker, contactGroupFactory,
						engine, invitationFactory, identityManager,
						blogManager);
	}

	@Test
	public void testAddingContactFreshState() throws Exception {
		Map<MessageId, BdfDictionary> sessions = new HashMap<>(0);
		testAddingContact(sessions);
	}

	@Test
	public void testAddingContactExistingState() throws Exception {
		Map<MessageId, BdfDictionary> sessions = new HashMap<>(1);
		sessions.put(new MessageId(getRandomId()), new BdfDictionary());
		testAddingContact(sessions);
	}

	@Test(expected = DbException.class)
	public void testAddingContactMultipleSessions() throws Exception {
		Map<MessageId, BdfDictionary> sessions = new HashMap<>(2);
		sessions.put(new MessageId(getRandomId()), new BdfDictionary());
		sessions.put(new MessageId(getRandomId()), new BdfDictionary());
		testAddingContact(sessions);
	}

	@Test
	public void testRemovingBlogFreshState() throws Exception {
		Map<MessageId, BdfDictionary> sessions = new HashMap<>(0);
		testRemovingBlog(sessions);
	}

	@Test
	public void testRemovingBlogExistingState() throws Exception {
		Map<MessageId, BdfDictionary> sessions = new HashMap<>(1);
		sessions.put(new MessageId(getRandomId()), new BdfDictionary());
		testRemovingBlog(sessions);
	}

	@Test(expected = DbException.class)
	public void testRemovingBlogMultipleSessions() throws Exception {
		Map<MessageId, BdfDictionary> sessions = new HashMap<>(2);
		sessions.put(new MessageId(getRandomId()), new BdfDictionary());
		sessions.put(new MessageId(getRandomId()), new BdfDictionary());
		testRemovingBlog(sessions);
	}

	private void testAddingContact(Map<MessageId, BdfDictionary> sessions)
			throws Exception {
		Transaction txn = new Transaction(null, false);
		LocalAuthor localAuthor =
				new LocalAuthor(localAuthorId, "Local Author",
						getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
						getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
						System.currentTimeMillis());
		BdfDictionary meta = BdfDictionary
				.of(new BdfEntry(GROUP_KEY_CONTACT_ID, contactId.getInt()));
		Group localBlogGroup =
				new Group(new GroupId(getRandomId()), BlogManager.CLIENT_ID,
						getRandomBytes(42));
		Blog localBlog = new Blog(localBlogGroup, localAuthor, false);

		context.checking(new Expectations() {{
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					CLIENT_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).containsGroup(txn, contactGroup.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(db).setGroupVisibility(txn, contactId, contactGroup.getId(),
					SHARED);
			oneOf(clientHelper)
					.mergeGroupMetadata(txn, contactGroup.getId(), meta);
			oneOf(identityManager).getLocalAuthor(txn);
			will(returnValue(localAuthor));
			oneOf(blogManager).getPersonalBlog(localAuthor);
			will(returnValue(localBlog));
			oneOf(blogManager).getPersonalBlog(author);
			will(returnValue(blog));
		}});
		expectPreShareShareable(txn, contact, localBlog, sessions);
		expectPreShareShareable(txn, contact, blog, sessions);

		blogSharingManager.createLocalState(txn);
	}

	private void expectPreShareShareable(Transaction txn, Contact contact,
			Blog blog, Map<MessageId, BdfDictionary> sessions)
			throws Exception {
		Group contactGroup =
				new Group(new GroupId(getRandomId()), CLIENT_ID,
						getRandomBytes(42));
		BdfDictionary sessionDict = new BdfDictionary();
		Message message =
				new Message(new MessageId(getRandomId()), contactGroup.getId(),
						42L, getRandomBytes(1337));
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					CLIENT_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(sessionParser)
					.getSessionQuery(new SessionId(blog.getId().getBytes()));
			will(returnValue(sessionDict));
			oneOf(clientHelper)
					.getMessageMetadataAsDictionary(txn, contactGroup.getId(),
							sessionDict);
			will(returnValue(sessions));
			if (sessions.size() == 0) {
				oneOf(db).addGroup(txn, blog.getGroup());
				oneOf(db).setGroupVisibility(txn, contact.getId(),
						blog.getGroup().getId(), SHARED);
				oneOf(clientHelper)
						.createMessageForStoringMetadata(contactGroup.getId());
				will(returnValue(message));
				oneOf(db).addLocalMessage(txn, message, new Metadata(), false);
				oneOf(sessionEncoder).encodeSession(with(any(Session.class)));
				will(returnValue(sessionDict));
				oneOf(clientHelper).mergeMessageMetadata(txn, message.getId(),
						sessionDict);
			}
		}});
	}

	private void testRemovingBlog(Map<MessageId, BdfDictionary> sessions)
			throws Exception {
		Transaction txn = new Transaction(null, false);
		BdfDictionary sessionDict = new BdfDictionary();
		Session session = new Session(contactGroup.getId(), blog.getId());

		context.checking(new Expectations() {{
			oneOf(db).getContacts(txn);
			will(returnValue(contacts));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					CLIENT_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(sessionParser)
					.getSessionQuery(new SessionId(blog.getId().getBytes()));
			will(returnValue(sessionDict));
			oneOf(clientHelper)
					.getMessageMetadataAsDictionary(txn, contactGroup.getId(),
							sessionDict);
			will(returnValue(sessions));
			if (sessions.size() == 1) {
				oneOf(sessionParser)
						.parseSession(contactGroup.getId(), sessionDict);
				will(returnValue(session));
				oneOf(engine).onLeaveAction(txn, session);
				will(returnValue(session));
				oneOf(sessionEncoder).encodeSession(session);
				will(returnValue(sessionDict));
				oneOf(clientHelper).mergeMessageMetadata(txn,
						sessions.keySet().iterator().next(), sessionDict);
			}
		}});
		blogSharingManager.removingBlog(txn, blog);
	}

}
