package org.briarproject.briar.test;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
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
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.test.TestData.AUTHOR_NAMES;
import static org.briarproject.briar.test.TestData.GROUP_NAMES;

public class TestDataCreatorImpl implements TestDataCreator {

	private final static int NUM_CONTACTS = 20;
	private final static int NUM_PRIVATE_MSGS = 15;
	private final static int NUM_BLOG_POSTS = 30;
	private final static int NUM_FORUMS = 3;
	private final static int NUM_FORUM_POSTS = 30;

	private final Logger LOG =
			Logger.getLogger(TestDataCreatorImpl.class.getName());

	private final AuthorFactory authorFactory;
	private final Clock clock;
	private final PrivateMessageFactory privateMessageFactory;
	private final ClientHelper clientHelper;
	private final MessageTracker messageTracker;
	private final BlogPostFactory blogPostFactory;
	private final CryptoComponent cryptoComponent;

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
			BlogPostFactory blogPostFactory, CryptoComponent cryptoComponent,
			DatabaseComponent db, IdentityManager identityManager,
			ContactManager contactManager,
			TransportPropertyManager transportPropertyManager,
			MessagingManager messagingManager, BlogManager blogManager,
			ForumManager forumManager, @IoExecutor Executor ioExecutor) {
		this.authorFactory = authorFactory;
		this.clock = clock;
		this.privateMessageFactory = privateMessageFactory;
		this.clientHelper = clientHelper;
		this.messageTracker = messageTracker;
		this.blogPostFactory = blogPostFactory;
		this.cryptoComponent = cryptoComponent;
		this.db = db;
		this.identityManager = identityManager;
		this.contactManager = contactManager;
		this.transportPropertyManager = transportPropertyManager;
		this.messagingManager = messagingManager;
		this.blogManager = blogManager;
		this.forumManager = forumManager;
		this.ioExecutor = ioExecutor;
	}

	public void createTestData() {
		ioExecutor.execute(() -> {
			try {
				createTestDataOnDbExecutor();
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, "Creating test data failed", e);
			}
		});
	}

	@IoExecutor
	private void createTestDataOnDbExecutor() throws DbException {
		List<Contact> contacts = createContacts();
		createPrivateMessages(contacts);
		createBlogPosts(contacts);
		List<Forum> forums = createForums(contacts);

		for (Forum forum : forums) {
			createRandomForumPosts(forum, contacts);
		}
	}

	private List<Contact> createContacts() throws DbException {
		List<Contact> contacts = new ArrayList<>(NUM_CONTACTS);
		LocalAuthor localAuthor = identityManager.getLocalAuthor();
		for (int i = 0; i < NUM_CONTACTS; i++) {
			Contact contact = addRandomContact(localAuthor);
			contacts.add(contact);
		}
		return contacts;
	}

	private Contact addRandomContact(LocalAuthor localAuthor)
			throws DbException {

		// prepare to add contact
		LocalAuthor author = getRandomAuthor();
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
					.addContact(txn, author, localAuthor.getId(), secretKey,
							timestamp, true, verified, true);
			transportPropertyManager.addRemoteProperties(txn, contactId, props);
			contact = db.getContact(txn, contactId);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}

		if (LOG.isLoggable(INFO)) {
			LOG.info("Added contact " + author.getName());
			LOG.info("with transport properties: " + props.toString());
		}
		localAuthors.put(contact, author);
		return contact;
	}

	private LocalAuthor getRandomAuthor() {
		int i = random.nextInt(AUTHOR_NAMES.length);
		String authorName = AUTHOR_NAMES[i];
		KeyPair keyPair = cryptoComponent.generateSignatureKeyPair();
		byte[] publicKey = keyPair.getPublic().getEncoded();
		byte[] privateKey = keyPair.getPrivate().getEncoded();
		return authorFactory
				.createLocalAuthor(authorName, publicKey, privateKey);
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
		bt.put(BluetoothConstants.PROP_ADDRESS, btAddress);
		props.put(BluetoothConstants.ID, bt);

		// LAN
		TransportProperties lan = new TransportProperties();
		String lanAddress = getRandomLanAddress();
		lan.put(LanTcpConstants.PROP_IP_PORTS, lanAddress);
		props.put(LanTcpConstants.ID, lan);

		// Tor
		TransportProperties tor = new TransportProperties();
		String torAddress = getRandomTorAddress();
		tor.put(TorConstants.PROP_ONION, torAddress);
		props.put(TorConstants.ID, tor);

		return props;
	}

	private String getRandomBluetoothAddress() {
		byte[] mac = new byte[6];
		random.nextBytes(mac);

		StringBuilder sb = new StringBuilder(18);
		for (byte b : mac) {
			if (sb.length() > 0) sb.append(":");
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	private String getRandomLanAddress() {
		StringBuilder sb = new StringBuilder();
		// address
		for (int i = 0; i < 4; i++) {
			if (sb.length() > 0) sb.append(".");
			sb.append(random.nextInt(256));
		}
		// port
		sb.append(":");
		sb.append(1024 + random.nextInt(50000));
		return sb.toString();
	}

	private String getRandomTorAddress() {
		StringBuilder sb = new StringBuilder();
		// address
		for (int i = 0; i < 16; i++) {
			if (random.nextBoolean())
				sb.append(2 + random.nextInt(5));
			else
				sb.append((char) (random.nextInt(26) + 'a'));
		}
		return sb.toString();
	}

	private void createPrivateMessages(List<Contact> contacts)
			throws DbException {
		for (Contact contact : contacts) {
			Group group = messagingManager.getContactGroup(contact);
			for (int i = 0; i < NUM_PRIVATE_MSGS; i++) {
				try {
					createPrivateMessage(group.getId(), i);
				} catch (FormatException e) {
					throw new RuntimeException(e);
				}
			}
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info("Created " + NUM_PRIVATE_MSGS +
					" private messages per contact.");
		}
	}

	private void createPrivateMessage(GroupId groupId, int num)
			throws DbException, FormatException {
		long timestamp = clock.currentTimeMillis() - num * 60 * 1000;
		String body = getRandomText();
		PrivateMessage m = privateMessageFactory
				.createPrivateMessage(groupId, timestamp, body);

		boolean local = random.nextBoolean();
		BdfDictionary meta = new BdfDictionary();
		meta.put("timestamp", timestamp);
		meta.put("local", local);
		meta.put("read", local);  // all local messages are read

		Transaction txn = db.startTransaction(false);
		try {
			clientHelper.addLocalMessage(txn, m.getMessage(), meta, true);
			if (local) messageTracker.trackOutgoingMessage(txn, m.getMessage());
			else messageTracker.trackIncomingMessage(txn, m.getMessage());
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	private void createBlogPosts(List<Contact> contacts)
			throws DbException {
		for (int i = 0; i < NUM_BLOG_POSTS; i++) {
			Contact contact = contacts.get(random.nextInt(contacts.size()));
			LocalAuthor author = localAuthors.get(contact);
			addBlogPost(author, i);
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info("Created " + NUM_BLOG_POSTS + " blog posts.");
		}
	}

	private void addBlogPost(LocalAuthor author, int num) throws DbException {
		Blog blog = blogManager.getPersonalBlog(author);
		long timestamp = clock.currentTimeMillis() - num * 60 * 1000;
		String body = getRandomText();
		try {
			BlogPost blogPost = blogPostFactory
					.createBlogPost(blog.getId(), timestamp, null, author,
							body);
			blogManager.addLocalPost(blogPost);
		} catch (FormatException | GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private List<Forum> createForums(List<Contact> contacts)
			throws DbException {
		List<Forum> forums = new ArrayList<>(NUM_FORUMS);
		for (int i = 0; i < NUM_FORUMS; i++) {
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
			LOG.info("Created " + NUM_FORUMS + " forums with " +
					NUM_FORUM_POSTS + " posts each.");
		}
		return forums;
	}

	private void createRandomForumPosts(Forum forum, List<Contact> contacts)
			throws DbException {
		List<ForumPost> posts = new ArrayList<>();
		for (int i = 0; i < NUM_FORUM_POSTS; i++) {
			Contact contact = contacts.get(random.nextInt(contacts.size()));
			LocalAuthor author = localAuthors.get(contact);
			long timestamp = clock.currentTimeMillis() - i * 60 * 1000;
			String body = getRandomText();
			MessageId parent = null;
			if (random.nextBoolean() && posts.size() > 0) {
				ForumPost parentPost =
						posts.get(random.nextInt(posts.size()));
				parent = parentPost.getMessage().getId();
			}
			ForumPost post = forumManager
					.createLocalPost(forum.getId(), body, timestamp, parent,
							author);
			posts.add(post);
			forumManager.addLocalPost(post);
			if (random.nextBoolean()) {
				forumManager
						.setReadFlag(forum.getId(), post.getMessage().getId(),
								false);
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
