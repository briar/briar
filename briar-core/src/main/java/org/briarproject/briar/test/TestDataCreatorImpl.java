package org.briarproject.briar.test;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogPost;
import org.briarproject.briar.api.blog.BlogPostFactory;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumPost;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.api.test.TestDataCreator;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.bramble.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.UUID_BYTES;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.test.TestData.AUTHOR_NAMES;
import static org.briarproject.briar.test.TestData.GROUP_NAMES;

@NotNullByDefault
public class TestDataCreatorImpl implements TestDataCreator {

	private final Logger LOG =
			Logger.getLogger(TestDataCreatorImpl.class.getName());

	private final AuthorFactory authorFactory;
	private final Clock clock;
	private final PrivateMessageFactory privateMessageFactory;
	private final ClientHelper clientHelper;
	private final MessageTracker messageTracker;
	private final BlogPostFactory blogPostFactory;

	private final DatabaseComponent db;
	private final IdentityManager identityManager;
	private final ContactManager contactManager;
	private final TransportPropertyManager transportPropertyManager;
	private final MessagingManager messagingManager;
	private final BlogManager blogManager;
	private final ForumManager forumManager;

	@IoExecutor
	private final Executor ioExecutor;

	private final Random random = new Random();
	private final Map<Contact, LocalAuthor> localAuthors = new HashMap<>();

	@Inject
	TestDataCreatorImpl(AuthorFactory authorFactory, Clock clock,
			PrivateMessageFactory privateMessageFactory,
			ClientHelper clientHelper, MessageTracker messageTracker,
			BlogPostFactory blogPostFactory, DatabaseComponent db,
			IdentityManager identityManager, ContactManager contactManager,
			TransportPropertyManager transportPropertyManager,
			MessagingManager messagingManager, BlogManager blogManager,
			ForumManager forumManager, @IoExecutor Executor ioExecutor) {
		this.authorFactory = authorFactory;
		this.clock = clock;
		this.privateMessageFactory = privateMessageFactory;
		this.clientHelper = clientHelper;
		this.messageTracker = messageTracker;
		this.blogPostFactory = blogPostFactory;
		this.db = db;
		this.identityManager = identityManager;
		this.contactManager = contactManager;
		this.transportPropertyManager = transportPropertyManager;
		this.messagingManager = messagingManager;
		this.blogManager = blogManager;
		this.forumManager = forumManager;
		this.ioExecutor = ioExecutor;
	}

	@Override
	public void createTestData(int numContacts, int numPrivateMsgs,
			int numBlogPosts, int numForums, int numForumPosts) {
		if (numContacts == 0) throw new IllegalArgumentException(
				"Number of contacts must be >= 1");
		ioExecutor.execute(() -> {
			try {
				createTestDataOnIoExecutor(numContacts, numPrivateMsgs,
						numBlogPosts, numForums, numForumPosts);
			} catch (DbException e) {
				LOG.log(WARNING, "Creating test data failed", e);
			}
		});
	}

	@IoExecutor
	private void createTestDataOnIoExecutor(int numContacts, int numPrivateMsgs,
			int numBlogPosts, int numForums, int numForumPosts)
			throws DbException {
		List<Contact> contacts = createContacts(numContacts);
		createPrivateMessages(contacts, numPrivateMsgs);
		createBlogPosts(contacts, numBlogPosts);
		List<Forum> forums = createForums(contacts, numForums, numForumPosts);

		for (Forum forum : forums) {
			createRandomForumPosts(forum, contacts, numForumPosts);
		}
	}

	private List<Contact> createContacts(int numContacts) throws DbException {
		List<Contact> contacts = new ArrayList<>(numContacts);
		LocalAuthor localAuthor = identityManager.getLocalAuthor();
		for (int i = 0; i < numContacts; i++) {
			LocalAuthor remote = getRandomAuthor();
			Contact contact = addContact(localAuthor.getId(), remote);
			contacts.add(contact);
		}
		return contacts;
	}

	private Contact addContact(AuthorId localAuthorId, LocalAuthor remote)
			throws DbException {

		// prepare to add contact
		SecretKey secretKey = getSecretKey();
		long timestamp = clock.currentTimeMillis();
		boolean verified = random.nextBoolean();

		// prepare transport properties
		Map<TransportId, TransportProperties> props =
				getRandomTransportProperties();

		Contact contact;
		Transaction txn = db.startTransaction(false);
		try {
			ContactId contactId = contactManager
					.addContact(txn, remote, localAuthorId, secretKey,
							timestamp, true, verified, true);
			if (random.nextBoolean()) {
				contactManager
						.setContactAlias(txn, contactId, getRandomAuthorName());
			}
			transportPropertyManager.addRemoteProperties(txn, contactId, props);
			contact = db.getContact(txn, contactId);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}

		if (LOG.isLoggable(INFO)) {
			LOG.info("Added contact " + remote.getName());
			LOG.info("with transport properties: " + props.toString());
		}
		localAuthors.put(contact, remote);
		return contact;
	}

	@Override
	public Contact addContact(String name) throws DbException {
		LocalAuthor localAuthor = identityManager.getLocalAuthor();
		LocalAuthor remote = authorFactory.createLocalAuthor(name);
		return addContact(localAuthor.getId(), remote);
	}

	private String getRandomAuthorName() {
		int i = random.nextInt(AUTHOR_NAMES.length);
		return AUTHOR_NAMES[i];
	}

	private LocalAuthor getRandomAuthor() {
		return authorFactory.createLocalAuthor(getRandomAuthorName());
	}

	private SecretKey getSecretKey() {
		byte[] b = new byte[SecretKey.LENGTH];
		random.nextBytes(b);
		return new SecretKey(b);
	}

	private Map<TransportId, TransportProperties> getRandomTransportProperties() {
		Map<TransportId, TransportProperties> props = new HashMap<>();

		// Bluetooth
		TransportProperties bt = new TransportProperties();
		String btAddress = getRandomBluetoothAddress();
		String uuid = getRandomUUID();
		bt.put(BluetoothConstants.PROP_ADDRESS, btAddress);
		bt.put(BluetoothConstants.PROP_UUID, uuid);
		props.put(BluetoothConstants.ID, bt);

		// LAN
		TransportProperties lan = new TransportProperties();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			if (sb.length() > 0) sb.append(',');
			sb.append(getRandomLanAddress());
		}
		lan.put(LanTcpConstants.PROP_IP_PORTS, sb.toString());
		String port = String.valueOf(getRandomPortNumber());
		lan.put(LanTcpConstants.PROP_PORT, port);
		props.put(LanTcpConstants.ID, lan);

		// Tor
		TransportProperties tor = new TransportProperties();
		String torAddress = getRandomTorAddress();
		tor.put(TorConstants.PROP_ONION_V2, torAddress);
		props.put(TorConstants.ID, tor);

		return props;
	}

	private String getRandomBluetoothAddress() {
		byte[] mac = new byte[6];
		random.nextBytes(mac);

		StringBuilder sb = new StringBuilder(18);
		for (byte b : mac) {
			if (sb.length() > 0) sb.append(":");
			sb.append(String.format("%02X", b));
		}
		return sb.toString();
	}

	private String getRandomUUID() {
		byte[] uuid = new byte[UUID_BYTES];
		random.nextBytes(uuid);
		return UUID.nameUUIDFromBytes(uuid).toString();
	}

	private String getRandomLanAddress() {
		StringBuilder sb = new StringBuilder();
		// address
		if (random.nextInt(5) == 0) {
			sb.append("10.");
			sb.append(random.nextInt(2)).append('.');
			sb.append(random.nextInt(2)).append('.');
			sb.append(random.nextInt(255));
		} else {
			sb.append("192.168.");
			sb.append(random.nextInt(2)).append('.');
			sb.append(random.nextInt(255));
		}
		// port
		sb.append(':').append(getRandomPortNumber());
		return sb.toString();
	}

	private int getRandomPortNumber() {
		return 32768 + random.nextInt(32768);
	}

	private String getRandomTorAddress() {
		StringBuilder sb = new StringBuilder();
		// address
		for (int i = 0; i < 16; i++) {
			if (random.nextBoolean()) sb.append(2 + random.nextInt(6));
			else sb.append((char) (random.nextInt(26) + 'a'));
		}
		return sb.toString();
	}

	private void createPrivateMessages(List<Contact> contacts,
			int numPrivateMsgs) throws DbException {
		for (Contact contact : contacts) {
			Group group = messagingManager.getContactGroup(contact);
			for (int i = 0; i < numPrivateMsgs; i++) {
				try {
					createRandomPrivateMessage(group.getId(), i);
				} catch (FormatException e) {
					throw new RuntimeException(e);
				}
			}
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info("Created " + numPrivateMsgs +
					" private messages per contact.");
		}
	}

	private void createRandomPrivateMessage(GroupId groupId, int num)
			throws DbException, FormatException {
		long timestamp = clock.currentTimeMillis() - num * 60 * 1000;
		String text = getRandomText();
		boolean local = random.nextBoolean();
		boolean autoDelete = random.nextBoolean();
		createPrivateMessage(groupId, text, timestamp, local, autoDelete);
	}

	private void createPrivateMessage(GroupId groupId, String text,
			long timestamp, boolean local, boolean autoDelete)
			throws DbException, FormatException {
		long timer = autoDelete ?
				MIN_AUTO_DELETE_TIMER_MS : NO_AUTO_DELETE_TIMER;
		PrivateMessage m = privateMessageFactory.createPrivateMessage(groupId,
				timestamp, text, emptyList(), timer);
		BdfDictionary meta = new BdfDictionary();
		meta.put("timestamp", timestamp);
		meta.put("local", local);
		meta.put("read", local);  // all local messages are read

		Transaction txn = db.startTransaction(false);
		try {
			clientHelper.addLocalMessage(txn, m.getMessage(), meta, true,
					false);
			if (local) messageTracker.trackOutgoingMessage(txn, m.getMessage());
			else messageTracker.trackIncomingMessage(txn, m.getMessage());
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	private void createBlogPosts(List<Contact> contacts, int numBlogPosts)
			throws DbException {
		for (int i = 0; i < numBlogPosts; i++) {
			Contact contact = contacts.get(random.nextInt(contacts.size()));
			LocalAuthor author = localAuthors.get(contact);
			addBlogPost(author, i);
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info("Created " + numBlogPosts + " blog posts.");
		}
	}

	private void addBlogPost(LocalAuthor author, int num) throws DbException {
		Blog blog = blogManager.getPersonalBlog(author);
		long timestamp = clock.currentTimeMillis() - num * 60 * 1000;
		String text = getRandomText();
		try {
			BlogPost blogPost = blogPostFactory.createBlogPost(blog.getId(),
					timestamp, null, author, text);
			blogManager.addLocalPost(blogPost);
		} catch (FormatException | GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private List<Forum> createForums(List<Contact> contacts, int numForums,
			int numForumPosts) throws DbException {
		List<Forum> forums = new ArrayList<>(numForums);
		for (int i = 0; i < numForums; i++) {
			// create forum
			String name = GROUP_NAMES[random.nextInt(GROUP_NAMES.length)];
			Forum forum = forumManager.addForum(name);

			// share with all contacts
			Transaction txn = db.startTransaction(false);
			try {
				for (Contact c : contacts) {
					db.setGroupVisibility(txn, c.getId(), forum.getId(),
							SHARED);
				}
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
			forums.add(forum);
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info("Created " + numForums + " forums with " +
					numForumPosts + " posts each.");
		}
		return forums;
	}

	private void createRandomForumPosts(Forum forum, List<Contact> contacts,
			int numForumPosts) throws DbException {
		List<ForumPost> posts = new ArrayList<>();
		for (int i = 0; i < numForumPosts; i++) {
			Contact contact = contacts.get(random.nextInt(contacts.size()));
			LocalAuthor author = localAuthors.get(contact);
			long timestamp = clock.currentTimeMillis() - i * 60 * 1000;
			String text = getRandomText();
			MessageId parent = null;
			if (random.nextBoolean() && posts.size() > 0) {
				ForumPost parentPost =
						posts.get(random.nextInt(posts.size()));
				parent = parentPost.getMessage().getId();
			}
			ForumPost post = forumManager.createLocalPost(forum.getId(), text,
					timestamp, parent, author);
			posts.add(post);
			forumManager.addLocalPost(post);
			if (random.nextBoolean()) {
				forumManager.setReadFlag(forum.getId(),
						post.getMessage().getId(), false);
			}
		}
	}

	private String getRandomText() {
		int minLength = 3 + random.nextInt(500);
		int maxWordLength = 15;
		StringBuilder sb = new StringBuilder();
		while (sb.length() < minLength) {
			if (sb.length() > 0) sb.append(' ');
			sb.append(getRandomString(random.nextInt(maxWordLength) + 1));
		}
		if (random.nextBoolean()) {
			sb.append(" \uD83D\uDC96 \uD83E\uDD84 \uD83C\uDF08");
		}
		return sb.toString();
	}

}
