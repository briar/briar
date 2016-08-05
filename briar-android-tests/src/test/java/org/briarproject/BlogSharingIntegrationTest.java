package org.briarproject;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogInvitationRequest;
import org.briarproject.api.blogs.BlogInvitationResponse;
import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogPostFactory;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.BlogInvitationReceivedEvent;
import org.briarproject.api.event.BlogInvitationResponseReceivedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageStateChangedEvent;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.sync.ValidationManager.State;
import org.briarproject.api.system.Clock;
import org.briarproject.blogs.BlogsModule;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sharing.SharingModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.TestPluginsModule.MAX_LATENCY;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlogSharingIntegrationTest extends BriarTestCase {

	private LifecycleManager lifecycleManager0, lifecycleManager1,
			lifecycleManager2;
	private SyncSessionFactory sync0, sync1, sync2;
	private BlogManager blogManager0, blogManager1, blogManager2;
	private ContactManager contactManager0, contactManager1, contactManager2;
	private Contact contact1, contact2, contact01, contact02;
	private ContactId contactId1, contactId2, contactId01, contactId02;
	private IdentityManager identityManager0, identityManager1,
			identityManager2;
	private LocalAuthor author0, author1, author2;
	private Blog blog0, blog1, blog2;
	private SharerListener listener0, listener2;
	private InviteeListener listener1;

	@Inject
	Clock clock;
	@Inject
	AuthorFactory authorFactory;
	@Inject
	BlogPostFactory blogPostFactory;
	@Inject
	CryptoComponent cryptoComponent;

	// objects accessed from background threads need to be volatile
	private volatile BlogSharingManager blogSharingManager0;
	private volatile BlogSharingManager blogSharingManager1;
	private volatile BlogSharingManager blogSharingManager2;
	private volatile Waiter eventWaiter;
	private volatile Waiter msgWaiter;

	private final File testDir = TestUtils.getTestDirectory();
	private final SecretKey master = TestUtils.getSecretKey();
	private final int TIMEOUT = 15000;
	private final String SHARER = "Sharer";
	private final String INVITEE = "Invitee";
	private final String CONTACT2 = "Contact2";

	private static final Logger LOG =
			Logger.getLogger(BlogSharingIntegrationTest.class.getName());

	private BlogSharingIntegrationTestComponent t0, t1, t2;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	public void setUp() {
		BlogSharingIntegrationTestComponent component =
				DaggerBlogSharingIntegrationTestComponent.builder().build();
		component.inject(this);
		injectEagerSingletons(component);

		assertTrue(testDir.mkdirs());
		File t0Dir = new File(testDir, SHARER);
		t0 = DaggerBlogSharingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(t0);
		File t1Dir = new File(testDir, INVITEE);
		t1 = DaggerBlogSharingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(t1);
		File t2Dir = new File(testDir, CONTACT2);
		t2 = DaggerBlogSharingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t2Dir)).build();
		injectEagerSingletons(t2);

		identityManager0 = t0.getIdentityManager();
		identityManager1 = t1.getIdentityManager();
		identityManager2 = t2.getIdentityManager();
		contactManager0 = t0.getContactManager();
		contactManager1 = t1.getContactManager();
		contactManager2 = t2.getContactManager();
		blogManager0 = t0.getBlogManager();
		blogManager1 = t1.getBlogManager();
		blogManager2 = t2.getBlogManager();
		blogSharingManager0 = t0.getBlogSharingManager();
		blogSharingManager1 = t1.getBlogSharingManager();
		blogSharingManager2 = t2.getBlogSharingManager();
		sync0 = t0.getSyncSessionFactory();
		sync1 = t1.getSyncSessionFactory();
		sync2 = t2.getSyncSessionFactory();

		// initialize waiters fresh for each test
		eventWaiter = new Waiter();
		msgWaiter = new Waiter();
	}

	@Test
	public void testPersonalBlogCannotBeSharedWithOwner() throws Exception {
		startLifecycles();
		defaultInit(true);

		assertFalse(blogSharingManager0.canBeShared(blog1.getId(), contact1));
		assertFalse(blogSharingManager0.canBeShared(blog2.getId(), contact2));
		assertFalse(blogSharingManager1.canBeShared(blog0.getId(), contact01));
		assertFalse(blogSharingManager2.canBeShared(blog0.getId(), contact02));

		// create invitation
		blogSharingManager0
				.sendInvitation(blog1.getId(), contactId1, "Hi!");

		// sync invitation
		sync0To1();
		// make sure the invitee ignored the request for their own blog
		assertFalse(listener1.requestReceived);

		stopLifecycles();
	}

	@Test
	public void testSuccessfulSharing() throws Exception {
		startLifecycles();

		// initialize and let invitee accept all requests
		defaultInit(true);

		// send invitation
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1, "Hi!");

		// invitee has own blog and that of the sharer
		assertEquals(2, blogManager1.getBlogs().size());

		// sync first request message
		sync0To1();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was added successfully
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(3, blogManager1.getBlogs().size());

		// invitee has one invitation message from sharer
		List<InvitationMessage> list =
				new ArrayList<>(blogSharingManager1
						.getInvitationMessages(contactId01));
		assertEquals(2, list.size());
		// check other things are alright with the message
		for (InvitationMessage m : list) {
			if (m instanceof BlogInvitationRequest) {
				BlogInvitationRequest invitation =
						(BlogInvitationRequest) m;
				assertFalse(invitation.isAvailable());
				assertEquals(blog2.getAuthor().getName(),
						invitation.getBlogAuthorName());
				assertEquals(contactId1, invitation.getContactId());
				assertEquals("Hi!", invitation.getMessage());
			} else {
				BlogInvitationResponse response =
						(BlogInvitationResponse) m;
				assertEquals(contactId01, response.getContactId());
				assertTrue(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2,
				blogSharingManager0.getInvitationMessages(contactId1)
						.size());
		// blog can not be shared again
		assertFalse(blogSharingManager0.canBeShared(blog2.getId(), contact1));
		assertFalse(blogSharingManager1.canBeShared(blog2.getId(), contact01));

		stopLifecycles();
	}

	@Test
	public void testDeclinedSharing() throws Exception {
		startLifecycles();

		// initialize and let invitee deny all requests
		defaultInit(false);

		// send invitation
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1, null);

		// sync first request message
		sync0To1();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was not added
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(2, blogManager1.getBlogs().size());
		// blog is no longer available to invitee who declined
		assertEquals(0, blogSharingManager1.getInvitations().size());

		// invitee has one invitation message from sharer and one response
		List<InvitationMessage> list =
				new ArrayList<>(blogSharingManager1
						.getInvitationMessages(contactId01));
		assertEquals(2, list.size());
		// check things are alright with the  message
		for (InvitationMessage m : list) {
			if (m instanceof BlogInvitationRequest) {
				BlogInvitationRequest invitation =
						(BlogInvitationRequest) m;
				assertFalse(invitation.isAvailable());
				assertEquals(blog2.getAuthor().getName(),
						invitation.getBlogAuthorName());
				assertEquals(contactId1, invitation.getContactId());
				assertEquals(null, invitation.getMessage());
			} else {
				BlogInvitationResponse response =
						(BlogInvitationResponse) m;
				assertEquals(contactId01, response.getContactId());
				assertFalse(response.wasAccepted());
				assertTrue(response.isLocal());
			}
		}
		// sharer has own invitation message and response
		assertEquals(2,
				blogSharingManager0.getInvitationMessages(contactId1)
						.size());
		// blog can be shared again
		assertTrue(blogSharingManager0.canBeShared(blog2.getId(), contact1));

		stopLifecycles();
	}

	@Test
	public void testInviteeLeavesAfterFinished() throws Exception {
		startLifecycles();

		// initialize and let invitee accept all requests
		defaultInit(true);

		// send invitation
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1, "Hi!");

		// sync first request message
		sync0To1();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was added successfully
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(3, blogManager1.getBlogs().size());
		assertTrue(blogManager1.getBlogs().contains(blog2));

		// sharer shares blog with invitee
		assertTrue(blogSharingManager0.getSharedWith(blog2.getId())
				.contains(contact1));
		// invitee gets blog shared by sharer
		assertTrue(blogSharingManager1.getSharedBy(blog2.getId())
				.contains(contact01));

		// invitee un-subscribes from blog
		blogManager1.removeBlog(blog2);

		// send leave message to sharer
		sync1To0();

		// blog is gone
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(2, blogManager1.getBlogs().size());

		// sharer no longer shares blog with invitee
		assertFalse(blogSharingManager0.getSharedWith(blog2.getId())
				.contains(contact1));
		// invitee no longer gets blog shared by sharer
		assertFalse(blogSharingManager1.getSharedBy(blog2.getId())
				.contains(contact01));
		// blog can be shared again
		assertTrue(blogSharingManager0.canBeShared(blog2.getId(), contact1));
		assertTrue(blogSharingManager1.canBeShared(blog2.getId(), contact01));

		stopLifecycles();
	}

	@Test
	public void testInvitationForExistingBlog() throws Exception {
		startLifecycles();

		// initialize and let invitee accept all requests
		defaultInit(true);

		// 1 and 2 are adding each other
		contactManager1.addContact(author2,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		contactManager2.addContact(author1,
				author2.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		assertEquals(3, blogManager1.getBlogs().size());

		// sharer sends invitation for 2's blog to 1
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1, "Hi!");

		// sync first request message
		sync0To1();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// make sure blog2 is shared by 0
		Collection<Contact> contacts =
				blogSharingManager1.getSharedBy(blog2.getId());
		assertEquals(1, contacts.size());
		assertTrue(contacts.contains(contact01));

		// make sure 1 knows that they have blog2 already
		Collection<InvitationMessage> messages =
				blogSharingManager1.getInvitationMessages(contactId01);
		assertEquals(2, messages.size());
		assertEquals(blog2, blogManager1.getBlog(blog2.getId()));

		// sync response back
		sync1To0();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was not added, because it was there already
		assertEquals(0, blogSharingManager0.getInvitations().size());
		assertEquals(3, blogManager1.getBlogs().size());

		stopLifecycles();
	}

	@Test
	public void testRemovingSharedBlog() throws Exception {
		startLifecycles();

		// initialize and let invitee accept all requests
		defaultInit(true);

		// send invitation
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1, "Hi!");

		// sync first request message
		sync0To1();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync response back
		sync1To0();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was added successfully and is shard both ways
		assertEquals(3, blogManager1.getBlogs().size());
		Collection<Contact> sharedWith =
				blogSharingManager0.getSharedWith(blog2.getId());
		assertEquals(1, sharedWith.size());
		assertEquals(contact1, sharedWith.iterator().next());
		Collection<Contact> sharedBy =
				blogSharingManager1.getSharedBy(blog2.getId());
		assertEquals(1, sharedBy.size());
		assertEquals(contact01, sharedBy.iterator().next());

		// shared blog can be removed
		assertTrue(blogManager1.canBeRemoved(blog2.getId()));

		// invitee removes blog again
		blogManager1.removeBlog(blog2);

		// sync LEAVE message
		sync1To0();

		// sharer does not share this blog anymore with invitee
		sharedWith =
				blogSharingManager0.getSharedWith(blog2.getId());
		assertEquals(0, sharedWith.size());

		stopLifecycles();
	}

	@Test
	public void testSharedBlogBecomesPermanent() throws Exception {
		startLifecycles();

		// initialize and let invitee accept all requests
		defaultInit(true);

		// invitee only sees two blogs
		assertEquals(2, blogManager1.getBlogs().size());

		// sharer sends invitation for 2's blog to 1
		blogSharingManager0
				.sendInvitation(blog2.getId(), contactId1, "Hi!");

		// sync first request message
		sync0To1();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// make sure blog2 is shared by 0
		Collection<Contact> contacts =
				blogSharingManager1.getSharedBy(blog2.getId());
		assertEquals(1, contacts.size());
		assertTrue(contacts.contains(contact01));

		// sync response back
		sync1To0();
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.responseReceived);

		// blog was added and can be removed
		assertEquals(3, blogManager1.getBlogs().size());
		assertTrue(blogManager1.canBeRemoved(blog2.getId()));

		// 1 and 2 are adding each other
		contactManager1.addContact(author2,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		contactManager2.addContact(author1,
				author2.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		assertEquals(3, blogManager1.getBlogs().size());

		// now blog can not be removed anymore
		assertFalse(blogManager1.canBeRemoved(blog2.getId()));

		stopLifecycles();
	}


	@After
	public void tearDown() throws InterruptedException {
		TestUtils.deleteTestDirectory(testDir);
	}

	private class SharerListener implements EventListener {

		volatile boolean requestReceived = false;
		volatile boolean responseReceived = false;

		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent event = (MessageStateChangedEvent) e;
				State s = event.getState();
				ClientId c = event.getClientId();
				if ((s == DELIVERED || s == INVALID) &&
						c.equals(blogSharingManager0.getClientId()) &&
						!event.isLocal()) {
					LOG.info("TEST: Sharer received message in group " +
							event.getMessage().getGroupId().hashCode());
					msgWaiter.resume();
				} else if (s == DELIVERED && !event.isLocal() &&
						c.equals(blogManager0.getClientId())) {
					LOG.info("TEST: Sharer received blog post");
					msgWaiter.resume();
				}
			} else if (e instanceof BlogInvitationResponseReceivedEvent) {
				BlogInvitationResponseReceivedEvent event =
						(BlogInvitationResponseReceivedEvent) e;
				eventWaiter.assertEquals(contactId1, event.getContactId());
				responseReceived = true;
				eventWaiter.resume();
			}
			// this is only needed for tests where a blog is re-shared
			else if (e instanceof BlogInvitationReceivedEvent) {
				BlogInvitationReceivedEvent event =
						(BlogInvitationReceivedEvent) e;
				eventWaiter.assertEquals(contactId1, event.getContactId());
				requestReceived = true;
				Blog b = event.getBlog();
				try {
					Contact c = contactManager0.getContact(contactId1);
					blogSharingManager0.respondToInvitation(b, c, true);
				} catch (DbException ex) {
					eventWaiter.rethrow(ex);
				} finally {
					eventWaiter.resume();
				}
			}
		}
	}

	private class InviteeListener implements EventListener {

		volatile boolean requestReceived = false;
		volatile boolean responseReceived = false;

		private final boolean accept, answer;

		InviteeListener(boolean accept, boolean answer) {
			this.accept = accept;
			this.answer = answer;
		}

		InviteeListener(boolean accept) {
			this(accept, true);
		}

		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent event = (MessageStateChangedEvent) e;
				State s = event.getState();
				ClientId c = event.getClientId();
				if ((s == DELIVERED || s == INVALID) &&
						c.equals(blogSharingManager0.getClientId()) &&
						!event.isLocal()) {
					LOG.info("TEST: Invitee received message in group " +
							event.getMessage().getGroupId().hashCode());
					msgWaiter.resume();
				} else if (s == DELIVERED && !event.isLocal() &&
						c.equals(blogManager0.getClientId())) {
					LOG.info("TEST: Invitee received blog post");
					msgWaiter.resume();
				}
			} else if (e instanceof BlogInvitationReceivedEvent) {
				BlogInvitationReceivedEvent event =
						(BlogInvitationReceivedEvent) e;
				requestReceived = true;
				if (!answer) return;
				Blog b = event.getBlog();
				try {
					eventWaiter.assertEquals(1,
							blogSharingManager1.getInvitations().size());
					Contact c =
							contactManager1.getContact(event.getContactId());
					blogSharingManager1.respondToInvitation(b, c, accept);
				} catch (DbException ex) {
					eventWaiter.rethrow(ex);
				} finally {
					eventWaiter.resume();
				}
			}
			// this is only needed for tests where a blog is re-shared
			else if (e instanceof BlogInvitationResponseReceivedEvent) {
				BlogInvitationResponseReceivedEvent event =
						(BlogInvitationResponseReceivedEvent) e;
				eventWaiter.assertEquals(contactId01, event.getContactId());
				responseReceived = true;
				eventWaiter.resume();
			}
		}
	}

	private void startLifecycles() throws InterruptedException {
		// Start the lifecycle manager and wait for it to finish
		lifecycleManager0 = t0.getLifecycleManager();
		lifecycleManager1 = t1.getLifecycleManager();
		lifecycleManager2 = t2.getLifecycleManager();
		lifecycleManager0.startServices();
		lifecycleManager1.startServices();
		lifecycleManager2.startServices();
		lifecycleManager0.waitForStartup();
		lifecycleManager1.waitForStartup();
		lifecycleManager2.waitForStartup();
	}

	private void stopLifecycles() throws InterruptedException {
		// Clean up
		lifecycleManager0.stopServices();
		lifecycleManager1.stopServices();
		lifecycleManager2.stopServices();
		lifecycleManager0.waitForShutdown();
		lifecycleManager1.waitForShutdown();
		lifecycleManager2.waitForShutdown();
	}

	private void defaultInit(boolean accept) throws DbException {
		addDefaultIdentities();
		addDefaultContacts();
		getPersonalBlogOfSharer();
		listenToEvents(accept);
	}

	private void addDefaultIdentities() throws DbException {
		KeyPair keyPair = cryptoComponent.generateSignatureKeyPair();
		author0 = authorFactory.createLocalAuthor(SHARER,
				keyPair.getPublic().getEncoded(),
				keyPair.getPrivate().getEncoded());
		identityManager0.addLocalAuthor(author0);

		keyPair = cryptoComponent.generateSignatureKeyPair();
		author1 = authorFactory.createLocalAuthor(INVITEE,
				keyPair.getPublic().getEncoded(),
				keyPair.getPrivate().getEncoded());
		identityManager1.addLocalAuthor(author1);

		keyPair = cryptoComponent.generateSignatureKeyPair();
		author2 = authorFactory.createLocalAuthor(CONTACT2,
				keyPair.getPublic().getEncoded(),
				keyPair.getPrivate().getEncoded());
		identityManager2.addLocalAuthor(author2);
	}

	private void addDefaultContacts() throws DbException {
		// sharer adds invitee as contact
		contactId1 = contactManager0.addContact(author1,
				author0.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		contact1 = contactManager0.getContact(contactId1);
		// sharer adds second contact
		contactId2 = contactManager0.addContact(author2,
				author0.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		contact2 = contactManager0.getContact(contactId2);
		// contacts add sharer back
		contactId01 = contactManager1.addContact(author0,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		contact01 = contactManager1.getContact(contactId01);
		contactId02 = contactManager2.addContact(author0,
				author2.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		contact02 = contactManager2.getContact(contactId02);
	}

	private void getPersonalBlogOfSharer() throws DbException {
		blog0 = blogManager0.getPersonalBlog(author0);
		blog1 = blogManager0.getPersonalBlog(author1);
		blog2 = blogManager0.getPersonalBlog(author2);
	}

	private void listenToEvents(boolean accept) {
		listener0 = new SharerListener();
		t0.getEventBus().addListener(listener0);
		listener1 = new InviteeListener(accept);
		t1.getEventBus().addListener(listener1);
		listener2 = new SharerListener();
		t2.getEventBus().addListener(listener2);
	}

	private void sync0To1() throws IOException, TimeoutException {
		deliverMessage(sync0, contactId01, sync1, contactId1,
				"Sharer to Invitee");
	}

	private void sync1To0() throws IOException, TimeoutException {
		deliverMessage(sync1, contactId1, sync0, contactId01,
				"Invitee to Sharer");
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

		// wait for message to actually arrive
		msgWaiter.await(TIMEOUT, 1);
	}

	private void injectEagerSingletons(
			BlogSharingIntegrationTestComponent component) {

		component.inject(new LifecycleModule.EagerSingletons());
		component.inject(new BlogsModule.EagerSingletons());
		component.inject(new CryptoModule.EagerSingletons());
		component.inject(new ContactModule.EagerSingletons());
		component.inject(new TransportModule.EagerSingletons());
		component.inject(new SharingModule.EagerSingletons());
		component.inject(new SyncModule.EagerSingletons());
		component.inject(new PropertiesModule.EagerSingletons());
	}

}
