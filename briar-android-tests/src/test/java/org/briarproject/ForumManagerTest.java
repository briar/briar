package org.briarproject;

import junit.framework.Assert;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageStateChangedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sharing.SharingModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertFalse;
import static org.briarproject.TestPluginsModule.MAX_LATENCY;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.briarproject.api.sync.ValidationManager.State.PENDING;
import static org.junit.Assert.assertTrue;

public class ForumManagerTest extends BriarIntegrationTest {

	private LifecycleManager lifecycleManager0, lifecycleManager1;
	private SyncSessionFactory sync0, sync1;
	private ForumManager forumManager0, forumManager1;
	private ContactManager contactManager0, contactManager1;
	private ContactId contactId0,contactId1;
	private IdentityManager identityManager0, identityManager1;
	private LocalAuthor author0, author1;
	private Forum forum0;

	@Inject
	Clock clock;
	@Inject
	AuthorFactory authorFactory;
	@Inject
	CryptoComponent crypto;
	@Inject
	ForumPostFactory forumPostFactory;

	// objects accessed from background threads need to be volatile
	private volatile ForumSharingManager forumSharingManager0;
	private volatile ForumSharingManager forumSharingManager1;
	private volatile Waiter validationWaiter;
	private volatile Waiter deliveryWaiter;

	private final File testDir = TestUtils.getTestDirectory();
	private final SecretKey master = TestUtils.getSecretKey();
	private final int TIMEOUT = 15000;
	private final String SHARER = "Sharer";
	private final String INVITEE = "Invitee";

	private static final Logger LOG =
			Logger.getLogger(ForumSharingIntegrationTest.class.getName());

	private ForumManagerTestComponent t0, t1;

	@Before
	public void setUp() throws Exception {
		ForumManagerTestComponent component =
				DaggerForumManagerTestComponent.builder().build();
		component.inject(this);
		injectEagerSingletons(component);

		assertTrue(testDir.mkdirs());
		File t0Dir = new File(testDir, SHARER);
		t0 = DaggerForumManagerTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(t0);
		File t1Dir = new File(testDir, INVITEE);
		t1 = DaggerForumManagerTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(t1);

		identityManager0 = t0.getIdentityManager();
		identityManager1 = t1.getIdentityManager();
		contactManager0 = t0.getContactManager();
		contactManager1 = t1.getContactManager();
		forumManager0 = t0.getForumManager();
		forumManager1 = t1.getForumManager();
		forumSharingManager0 = t0.getForumSharingManager();
		forumSharingManager1 = t1.getForumSharingManager();
		sync0 = t0.getSyncSessionFactory();
		sync1 = t1.getSyncSessionFactory();

		// initialize waiters fresh for each test
		validationWaiter = new Waiter();
		deliveryWaiter = new Waiter();
	}

	private ForumPost createForumPost(GroupId groupId,
			@Nullable ForumPost parent, String body, long ms) throws Exception {
		return forumPostFactory.createPost(groupId, ms,
				parent == null ? null : parent.getMessage().getId(),
				author0, body);
	}

	@Test
	public void testForumPost() throws Exception {
		startLifecycles();
		addDefaultIdentities();
		Forum forum = forumManager0.addForum("TestForum");
		assertEquals(1, forumManager0.getForums().size());
		final long ms1 = clock.currentTimeMillis() - 1000L;
		final String body1 = "some forum text";
		final long ms2 = clock.currentTimeMillis();
		final String body2 = "some other forum text";
		ForumPost post1 =
				createForumPost(forum.getGroup().getId(), null, body1, ms1);
		assertEquals(ms1, post1.getMessage().getTimestamp());
		ForumPost post2 =
				createForumPost(forum.getGroup().getId(), post1, body2, ms2);
		assertEquals(ms2, post2.getMessage().getTimestamp());
		forumManager0.addLocalPost(post1);
		forumManager0.setReadFlag(forum.getGroup().getId(),
				post1.getMessage().getId(), true);
		assertGroupCount(forumManager0, forum.getGroup().getId(), 1, 0,
				post1.getMessage().getTimestamp());
		forumManager0.addLocalPost(post2);
		forumManager0.setReadFlag(forum.getGroup().getId(),
				post2.getMessage().getId(), false);
		assertGroupCount(forumManager0, forum.getGroup().getId(), 2, 1,
				post2.getMessage().getTimestamp());
		forumManager0.setReadFlag(forum.getGroup().getId(),
				post2.getMessage().getId(), false);
		assertGroupCount(forumManager0, forum.getGroup().getId(), 2, 1,
				post2.getMessage().getTimestamp());
		Collection<ForumPostHeader> headers =
				forumManager0.getPostHeaders(forum.getGroup().getId());
		assertEquals(2, headers.size());
		for (ForumPostHeader h : headers) {
			final String hBody = forumManager0.getPostBody(h.getId());

			boolean isPost1 = h.getId().equals(post1.getMessage().getId());
			boolean isPost2 = h.getId().equals(post2.getMessage().getId());
			Assert.assertTrue(isPost1 || isPost2);
			if (isPost1) {
				assertEquals(h.getTimestamp(), ms1);
				assertEquals(body1, hBody);
				assertNull(h.getParentId());
				assertTrue(h.isRead());
			}
			else {
				assertEquals(h.getTimestamp(), ms2);
				assertEquals(body2, hBody);
				assertEquals(h.getParentId(), post2.getParent());
				assertFalse(h.isRead());
			}
		}
		forumManager0.removeForum(forum);
		assertEquals(0, forumManager0.getForums().size());
		stopLifecycles();
	}

	@Test
	public void testForumPostDelivery() throws Exception {
		startLifecycles();
		defaultInit();

		// share forum
		GroupId g = forum0.getId();
		forumSharingManager0.sendInvitation(g, contactId1, null);
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);
		Contact c0 = contactManager1.getContact(contactId0);
		forumSharingManager1.respondToInvitation(forum0, c0, true);
		sync1To0();
		deliveryWaiter.await(TIMEOUT, 1);

		// add one forum post
		long time = clock.currentTimeMillis();
		ForumPost post1 = createForumPost(g, null, "a", time);
		forumManager0.addLocalPost(post1);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());
		assertGroupCount(forumManager0, g, 1, 0, time);
		assertGroupCount(forumManager1, g, 0, 0, 0);

		// send post to 1
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);
		assertEquals(1, forumManager1.getPostHeaders(g).size());
		assertGroupCount(forumManager1, g, 1, 1, time);

		// add another forum post
		long time2 = clock.currentTimeMillis();
		ForumPost post2 = createForumPost(g, null, "b", time2);
		forumManager1.addLocalPost(post2);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(2, forumManager1.getPostHeaders(g).size());
		assertGroupCount(forumManager0, g, 1, 0, time);
		assertGroupCount(forumManager1, g, 2, 1, time2);

		// send post to 0
		sync1To0();
		deliveryWaiter.await(TIMEOUT, 1);
		assertEquals(2, forumManager1.getPostHeaders(g).size());
		assertGroupCount(forumManager0, g, 2, 1, time2);

		stopLifecycles();
	}

	@Test
	public void testForumPostDeliveredAfterParent() throws Exception {
		startLifecycles();
		defaultInit();

		// share forum
		GroupId g = forum0.getId();
		forumSharingManager0.sendInvitation(g, contactId1, null);
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);
		Contact c0 = contactManager1.getContact(contactId0);
		forumSharingManager1.respondToInvitation(forum0, c0, true);
		sync1To0();
		deliveryWaiter.await(TIMEOUT, 1);

		// add one forum post without the parent
		long time = clock.currentTimeMillis();
		ForumPost post1 = createForumPost(g, null, "a", time);
		ForumPost post2 = createForumPost(g, post1, "a", time);
		forumManager0.addLocalPost(post2);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// send post to 1 without waiting for message delivery
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// now add the parent post as well
		forumManager0.addLocalPost(post1);
		assertEquals(2, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// and send it over to 1 and wait for a second message to be delivered
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 2);
		assertEquals(2, forumManager1.getPostHeaders(g).size());

		stopLifecycles();
	}

	@Test
	public void testForumPostWithParentInOtherGroup() throws Exception {
		startLifecycles();
		defaultInit();

		// share forum
		GroupId g = forum0.getId();
		forumSharingManager0.sendInvitation(g, contactId1, null);
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);
		Contact c0 = contactManager1.getContact(contactId0);
		forumSharingManager1.respondToInvitation(forum0, c0, true);
		sync1To0();
		deliveryWaiter.await(TIMEOUT, 1);

		// share a second forum
		Forum forum1 = forumManager0.addForum("Test Forum1");
		GroupId g1 = forum1.getId();
		forumSharingManager0.sendInvitation(g1, contactId1, null);
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);
		forumSharingManager1.respondToInvitation(forum1, c0, true);
		sync1To0();
		deliveryWaiter.await(TIMEOUT, 1);

		// add one forum post with a parent in another forum
		long time = clock.currentTimeMillis();
		ForumPost post1 = createForumPost(g1, null, "a", time);
		ForumPost post = createForumPost(g, post1, "b", time);
		forumManager0.addLocalPost(post);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// send the child post to 1
		sync0To1();
		validationWaiter.await(TIMEOUT, 1);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(0, forumManager1.getPostHeaders(g).size());

		// now also add the parent post which is in another group
		forumManager0.addLocalPost(post1);
		assertEquals(1, forumManager0.getPostHeaders(g1).size());
		assertEquals(0, forumManager1.getPostHeaders(g1).size());

		// send posts to 1
		sync0To1();
		deliveryWaiter.await(TIMEOUT, 1);
		assertEquals(1, forumManager0.getPostHeaders(g).size());
		assertEquals(1, forumManager0.getPostHeaders(g1).size());
		// the next line is critical, makes sure post doesn't show up
		assertEquals(0, forumManager1.getPostHeaders(g).size());
		assertEquals(1, forumManager1.getPostHeaders(g1).size());

		stopLifecycles();
	}

	@After
	public void tearDown() throws Exception {
		TestUtils.deleteTestDirectory(testDir);
	}

	private class Listener implements EventListener {
		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent event = (MessageStateChangedEvent) e;
				if (!event.isLocal()) {
					if (event.getState() == DELIVERED) {
						deliveryWaiter.resume();
					} else if (event.getState() == INVALID ||
							event.getState() == PENDING) {
						validationWaiter.resume();
					}
				}
			}
		}
	}

	private void defaultInit() throws DbException {
		addDefaultIdentities();
		addDefaultContacts();
		addForum();
		listenToEvents();
	}

	private void addDefaultIdentities() throws DbException {
		KeyPair keyPair0 = crypto.generateSignatureKeyPair();
		byte[] publicKey0 = keyPair0.getPublic().getEncoded();
		byte[] privateKey0 = keyPair0.getPrivate().getEncoded();
		author0 = authorFactory
				.createLocalAuthor(SHARER, publicKey0, privateKey0);
		identityManager0.addLocalAuthor(author0);

		KeyPair keyPair1 = crypto.generateSignatureKeyPair();
		byte[] publicKey1 = keyPair1.getPublic().getEncoded();
		byte[] privateKey1 = keyPair1.getPrivate().getEncoded();
		author1 = authorFactory
				.createLocalAuthor(INVITEE, publicKey1, privateKey1);
		identityManager1.addLocalAuthor(author1);
	}

	private void addDefaultContacts() throws DbException {
		// sharer adds invitee as contact
		contactId1 = contactManager0.addContact(author1,
				author0.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
		// invitee adds sharer back
		contactId0 = contactManager1.addContact(author0,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
	}

	private void addForum() throws DbException {
		forum0 = forumManager0.addForum("Test Forum");
	}

	private void listenToEvents() {
		Listener listener0 = new Listener();
		t0.getEventBus().addListener(listener0);
		Listener listener1 = new Listener();
		t1.getEventBus().addListener(listener1);
	}

	private void sync0To1() throws IOException, TimeoutException {
		deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");
	}

	private void sync1To0() throws IOException, TimeoutException {
		deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
	}

	private void deliverMessage(SyncSessionFactory fromSync, ContactId fromId,
			SyncSessionFactory toSync, ContactId toId, String debug)
			throws IOException, TimeoutException {

		if (debug != null) LOG.info("TEST: Sending message from " + debug);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Create an outgoing sync session
		SyncSession sessionFrom =
				fromSync.createSimplexOutgoingSession(toId, MAX_LATENCY, out);
		// Write whatever needs to be written
		sessionFrom.run();
		out.close();

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Create an incoming sync session
		SyncSession sessionTo = toSync.createIncomingSession(fromId, in);
		// Read whatever needs to be read
		sessionTo.run();
		in.close();
	}

	private void startLifecycles() throws InterruptedException {
		// Start the lifecycle manager and wait for it to finish
		lifecycleManager0 = t0.getLifecycleManager();
		lifecycleManager1 = t1.getLifecycleManager();
		lifecycleManager0.startServices();
		lifecycleManager1.startServices();
		lifecycleManager0.waitForStartup();
		lifecycleManager1.waitForStartup();
	}

	private void stopLifecycles() throws InterruptedException {
		// Clean up
		lifecycleManager0.stopServices();
		lifecycleManager1.stopServices();
		lifecycleManager0.waitForShutdown();
		lifecycleManager1.waitForShutdown();
	}

	private void injectEagerSingletons(ForumManagerTestComponent component) {
		component.inject(new LifecycleModule.EagerSingletons());
		component.inject(new ForumModule.EagerSingletons());
		component.inject(new CryptoModule.EagerSingletons());
		component.inject(new ContactModule.EagerSingletons());
		component.inject(new TransportModule.EagerSingletons());
		component.inject(new SharingModule.EagerSingletons());
		component.inject(new SyncModule.EagerSingletons());
		component.inject(new PropertiesModule.EagerSingletons());
	}

}
