package briarproject;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseModule;
import org.briarproject.TestUtils;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.IntroductionAbortedEvent;
import org.briarproject.api.event.IntroductionRequestReceivedEvent;
import org.briarproject.api.event.IntroductionResponseReceivedEvent;
import org.briarproject.api.event.IntroductionSucceededEvent;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.SessionId;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.introduction.IntroductionModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.TestPluginsModule.MAX_LATENCY;
import static org.briarproject.TestPluginsModule.TRANSPORT_ID;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntroductionIntegrationTest extends BriarTestCase {

	LifecycleManager lifecycleManager0, lifecycleManager1, lifecycleManager2;
	SyncSessionFactory sync0, sync1, sync2;
	ContactManager contactManager0, contactManager1, contactManager2;
	ContactId contactId0, contactId1, contactId2;
	IdentityManager identityManager0, identityManager1, identityManager2;
	LocalAuthor author0, author1, author2;

	@Inject
	Clock clock;
	@Inject
	AuthorFactory authorFactory;

	// objects accessed from background threads need to be volatile
	private volatile IntroductionManager introductionManager0;
	private volatile IntroductionManager introductionManager1;
	private volatile IntroductionManager introductionManager2;
	private volatile Waiter eventWaiter;
	private volatile Waiter msgWaiter;

	private final File testDir = TestUtils.getTestDirectory();
	private final SecretKey master = TestUtils.getSecretKey();
	private final int TIMEOUT = 15000;
	private final String INTRODUCER = "Introducer";
	private final String INTRODUCEE1 = "Introducee1";
	private final String INTRODUCEE2 = "Introducee2";

	private static final Logger LOG =
			Logger.getLogger(IntroductionIntegrationTest.class.getName());

	private IntroductionIntegrationTestComponent t0, t1, t2;

	@Before
	public void setUp() {
		IntroductionIntegrationTestComponent component =
				DaggerIntroductionIntegrationTestComponent.builder().build();
		component.inject(this);
		injectEagerSingletons(component);

		assertTrue(testDir.mkdirs());
		File t0Dir = new File(testDir, INTRODUCER);
		t0 = DaggerIntroductionIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(t0);
		File t1Dir = new File(testDir, INTRODUCEE1);
		t1 = DaggerIntroductionIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(t1);
		File t2Dir = new File(testDir, INTRODUCEE2);
		t2 = DaggerIntroductionIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t2Dir)).build();
		injectEagerSingletons(t2);

		identityManager0 = t0.getIdentityManager();
		identityManager1 = t1.getIdentityManager();
		identityManager2 = t2.getIdentityManager();
		contactManager0 = t0.getContactManager();
		contactManager1 = t1.getContactManager();
		contactManager2 = t2.getContactManager();
		introductionManager0 = t0.getIntroductionManager();
		introductionManager1 = t1.getIntroductionManager();
		introductionManager2 = t2.getIntroductionManager();
		sync0 = t0.getSyncSessionFactory();
		sync1 = t1.getSyncSessionFactory();
		sync2 = t2.getSyncSessionFactory();

		// initialize waiters fresh for each test
		eventWaiter = new Waiter();
		msgWaiter = new Waiter();
	}

	@Test
	public void testIntroductionSession() throws Exception {
		startLifecycles();
		try {
			// Add Identities
			addDefaultIdentities();

			// Add Transport Properties
			addTransportProperties();

			// Add introducees as contacts
			contactId1 = contactManager0.addContact(author1,
					author0.getId(), master, clock.currentTimeMillis(), true,
					true
			);
			contactId2 = contactManager0.addContact(author2,
					author0.getId(), master, clock.currentTimeMillis(), true,
					true
			);
			// Add introducer back
			contactId0 = contactManager1.addContact(author0,
					author1.getId(), master, clock.currentTimeMillis(), true,
					true
			);
			ContactId contactId02 = contactManager2.addContact(author0,
					author2.getId(), master, clock.currentTimeMillis(), true,
					true
			);
			assertTrue(contactId0.equals(contactId02));

			// listen to events
			IntroducerListener listener0 = new IntroducerListener();
			t0.getEventBus().addListener(listener0);
			IntroduceeListener listener1 = new IntroduceeListener(1, true);
			t1.getEventBus().addListener(listener1);
			IntroduceeListener listener2 = new IntroduceeListener(2, true);
			t2.getEventBus().addListener(listener2);

			// make introduction
			long time = clock.currentTimeMillis();
			Contact introducee1 = contactManager0.getContact(contactId1);
			Contact introducee2 = contactManager0.getContact(contactId2);
			introductionManager0
					.makeIntroduction(introducee1, introducee2, "Hi!", time);

			// sync first request message
			deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync second request message
			deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener2.requestReceived);

			// sync first response
			deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response1Received);

			// sync second response
			deliverMessage(sync2, contactId2, sync0, contactId0, "2 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response2Received);

			// sync forwarded responses to introducees
			deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");
			deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");

			// sync first ACK and its forward
			deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
			deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");

			// sync second ACK and its forward
			deliverMessage(sync2, contactId2, sync0, contactId0, "2 to 0");
			deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 2");

			// wait for introduction to succeed
			eventWaiter.await(TIMEOUT, 2);
			assertTrue(listener1.succeeded);
			assertTrue(listener2.succeeded);

			assertTrue(contactManager1
					.contactExists(author2.getId(), author1.getId()));
			assertTrue(contactManager2
					.contactExists(author1.getId(), author2.getId()));

			assertDefaultUiMessages();
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testIntroductionSessionFirstDecline() throws Exception {
		startLifecycles();
		try {
			// Add Identities
			addDefaultIdentities();

			// Add Transport Properties
			addTransportProperties();

			// Add introducees as contacts
			contactId1 = contactManager0.addContact(author1, author0.getId(),
					master, clock.currentTimeMillis(), true, true
			);
			contactId2 = contactManager0.addContact(author2, author0.getId(),
					master, clock.currentTimeMillis(), true, true
			);
			// Add introducer back
			contactId0 = contactManager1.addContact(author0, author1.getId(),
					master, clock.currentTimeMillis(), true, true
			);
			ContactId contactId02 = contactManager2.addContact(author0,
					author2.getId(), master, clock.currentTimeMillis(), true,
					true
			);
			assertTrue(contactId0.equals(contactId02));

			// listen to events
			IntroducerListener listener0 = new IntroducerListener();
			t0.getEventBus().addListener(listener0);
			IntroduceeListener listener1 = new IntroduceeListener(1, false);
			t1.getEventBus().addListener(listener1);
			IntroduceeListener listener2 = new IntroduceeListener(2, true);
			t2.getEventBus().addListener(listener2);

			// make introduction
			long time = clock.currentTimeMillis();
			Contact introducee1 = contactManager0.getContact(contactId1);
			Contact introducee2 = contactManager0.getContact(contactId2);
			introductionManager0
					.makeIntroduction(introducee1, introducee2, null, time);

			// sync request messages
			deliverMessage(sync0, contactId0, sync1, contactId1);
			deliverMessage(sync0, contactId0, sync2, contactId2);

			// wait for requests to arrive
			eventWaiter.await(TIMEOUT, 2);
			assertTrue(listener1.requestReceived);
			assertTrue(listener2.requestReceived);

			// sync first response
			deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response1Received);

			// sync second response
			deliverMessage(sync2, contactId2, sync0, contactId0, "2 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response2Received);

			// sync first forwarded response
			deliverMessage(sync0, contactId0, sync2, contactId2);

			// note how the introducer does not forward the second response,
			// because after the first decline the protocol finished

			assertFalse(listener1.succeeded);
			assertFalse(listener2.succeeded);

			assertFalse(contactManager1
					.contactExists(author2.getId(), author1.getId()));
			assertFalse(contactManager2
					.contactExists(author1.getId(), author2.getId()));

			assertEquals(2,
					introductionManager0.getIntroductionMessages(contactId1)
							.size());
			assertEquals(2,
					introductionManager0.getIntroductionMessages(contactId2)
							.size());
			assertEquals(2,
					introductionManager1.getIntroductionMessages(contactId0)
							.size());
			// introducee2 should also have the decline response of introducee1
			assertEquals(3,
					introductionManager2.getIntroductionMessages(contactId0)
							.size());
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testIntroductionSessionSecondDecline() throws Exception {
		startLifecycles();
		try {
			// Add Identities
			addDefaultIdentities();

			// Add Transport Properties
			addTransportProperties();

			// Add introducees as contacts
			contactId1 = contactManager0.addContact(author1, author0.getId(),
					master, clock.currentTimeMillis(), true, true
			);
			contactId2 = contactManager0.addContact(author2, author0.getId(),
					master, clock.currentTimeMillis(), true, true
			);
			// Add introducer back
			contactId0 = contactManager1.addContact(author0, author1.getId(),
					master, clock.currentTimeMillis(), false, true
			);
			ContactId contactId02 = contactManager2.addContact(author0,
					author2.getId(), master, clock.currentTimeMillis(), false,
					true
			);
			assertTrue(contactId0.equals(contactId02));

			// listen to events
			IntroducerListener listener0 = new IntroducerListener();
			t0.getEventBus().addListener(listener0);
			IntroduceeListener listener1 = new IntroduceeListener(1, true);
			t1.getEventBus().addListener(listener1);
			IntroduceeListener listener2 = new IntroduceeListener(2, false);
			t2.getEventBus().addListener(listener2);

			// make introduction
			long time = clock.currentTimeMillis();
			Contact introducee1 = contactManager0.getContact(contactId1);
			Contact introducee2 = contactManager0.getContact(contactId2);
			introductionManager0
					.makeIntroduction(introducee1, introducee2, null, time);

			// sync request messages
			deliverMessage(sync0, contactId0, sync1, contactId1);
			deliverMessage(sync0, contactId0, sync2, contactId2);

			// wait for requests to arrive
			eventWaiter.await(TIMEOUT, 2);
			assertTrue(listener1.requestReceived);
			assertTrue(listener2.requestReceived);

			// sync first response
			deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response1Received);

			// sync second response
			deliverMessage(sync2, contactId2, sync0, contactId0, "2 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response2Received);

			// sync both forwarded response
			deliverMessage(sync0, contactId0, sync2, contactId2);
			deliverMessage(sync0, contactId0, sync1, contactId1);

			assertFalse(contactManager1
					.contactExists(author2.getId(), author1.getId()));
			assertFalse(contactManager2
					.contactExists(author1.getId(), author2.getId()));

			assertEquals(2,
					introductionManager0.getIntroductionMessages(contactId1)
							.size());
			assertEquals(2,
					introductionManager0.getIntroductionMessages(contactId2)
							.size());
			// introducee1 also sees the decline response from introducee2
			assertEquals(3,
					introductionManager1.getIntroductionMessages(contactId0)
							.size());
			assertEquals(2,
					introductionManager2.getIntroductionMessages(contactId0)
							.size());
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testIntroductionSessionDelayedFirstDecline() throws Exception {
		startLifecycles();
		try {
			// Add Identities
			addDefaultIdentities();

			// Add Transport Properties
			addTransportProperties();

			// Add introducees as contacts
			contactId1 = contactManager0.addContact(author1, author0.getId(),
					master, clock.currentTimeMillis(), true, true
			);
			contactId2 = contactManager0.addContact(author2, author0.getId(),
					master, clock.currentTimeMillis(), true, true
			);
			// Add introducer back
			contactId0 = contactManager1.addContact(author0, author1.getId(),
					master, clock.currentTimeMillis(), false, true
			);
			ContactId contactId02 = contactManager2.addContact(author0,
					author2.getId(), master, clock.currentTimeMillis(), false,
					true
			);
			assertTrue(contactId0.equals(contactId02));

			// listen to events
			IntroducerListener listener0 = new IntroducerListener();
			t0.getEventBus().addListener(listener0);
			IntroduceeListener listener1 = new IntroduceeListener(1, false);
			t1.getEventBus().addListener(listener1);
			IntroduceeListener listener2 = new IntroduceeListener(2, false);
			t2.getEventBus().addListener(listener2);

			// make introduction
			long time = clock.currentTimeMillis();
			Contact introducee1 = contactManager0.getContact(contactId1);
			Contact introducee2 = contactManager0.getContact(contactId2);
			introductionManager0
					.makeIntroduction(introducee1, introducee2, null, time);

			// sync request messages
			deliverMessage(sync0, contactId0, sync1, contactId1);
			deliverMessage(sync0, contactId0, sync2, contactId2);

			// wait for requests to arrive
			eventWaiter.await(TIMEOUT, 2);
			assertTrue(listener1.requestReceived);
			assertTrue(listener2.requestReceived);

			// sync first response
			deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response1Received);

			// sync second response
			deliverMessage(sync2, contactId2, sync0, contactId0, "2 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response2Received);

			// sync first forwarded response
			deliverMessage(sync0, contactId0, sync2, contactId2);

			// note how the second response will not be forwarded anymore

			assertFalse(contactManager1
					.contactExists(author2.getId(), author1.getId()));
			assertFalse(contactManager2
					.contactExists(author1.getId(), author2.getId()));

			// since introducee2 was already in FINISHED state when
			// introducee1's response arrived, she ignores and deletes it
			assertDefaultUiMessages();
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testIntroductionToSameContact() throws Exception {
		startLifecycles();
		try {
			// Add Identities
			addDefaultIdentities();

			// Add Transport Properties
			addTransportProperties();

			// Add introducee as contact
			contactId1 = contactManager0.addContact(author1, author0.getId(),
					master, clock.currentTimeMillis(), true, true
			);
			// Add introducer back
			contactId0 = contactManager1.addContact(author0, author1.getId(),
					master, clock.currentTimeMillis(), true, true
			);

			// listen to events
			IntroducerListener listener0 = new IntroducerListener();
			t0.getEventBus().addListener(listener0);
			IntroduceeListener listener1 = new IntroduceeListener(1, true);
			t1.getEventBus().addListener(listener1);

			// make introduction
			long time = clock.currentTimeMillis();
			Contact introducee1 = contactManager0.getContact(contactId1);
			introductionManager0
					.makeIntroduction(introducee1, introducee1, null, time);

			// sync request messages
			deliverMessage(sync0, contactId0, sync1, contactId1);

			// we should not get any event, because the request will be discarded
			assertFalse(listener1.requestReceived);

			// make really sure we don't have that request
			assertTrue(introductionManager1.getIntroductionMessages(contactId0)
					.isEmpty());
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testIntroductionToIdentitiesOfSameContact() throws Exception {
		startLifecycles();
		try {
			// Add Identities
			author0 = authorFactory.createLocalAuthor(INTRODUCER,
					TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
					TestUtils.getRandomBytes(123));
			identityManager0.addLocalAuthor(author0);
			author1 = authorFactory.createLocalAuthor(INTRODUCEE1,
					TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
					TestUtils.getRandomBytes(123));
			identityManager1.addLocalAuthor(author1);
			author2 = authorFactory.createLocalAuthor(INTRODUCEE2,
					TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
					TestUtils.getRandomBytes(123));
			identityManager1.addLocalAuthor(author2);

			// Add Transport Properties
			addTransportProperties();

			// Add introducees' authors as contacts
			contactId1 = contactManager0.addContact(author1,
					author0.getId(), master, clock.currentTimeMillis(), true,
					true
			);
			contactId2 = contactManager0.addContact(author2,
					author0.getId(), master, clock.currentTimeMillis(), true,
					true
			);
			// Add introducer back
			contactId0 = null;
			ContactId contactId01 = contactManager1.addContact(author0,
					author1.getId(), master, clock.currentTimeMillis(), false,
					true
			);
			ContactId contactId02 = contactManager1.addContact(author0,
					author2.getId(), master, clock.currentTimeMillis(), false,
					true
			);

			// listen to events
			IntroducerListener listener0 = new IntroducerListener();
			t0.getEventBus().addListener(listener0);
			IntroduceeListener listener1 = new IntroduceeListener(1, true);
			t1.getEventBus().addListener(listener1);

			// make introduction
			long time = clock.currentTimeMillis();
			Contact introducee1 = contactManager0.getContact(contactId1);
			Contact introducee2 = contactManager0.getContact(contactId2);
			introductionManager0
					.makeIntroduction(introducee1, introducee2, "Hi!", time);

			// sync request messages
			deliverMessage(sync0, contactId01, sync1, contactId1);
			deliverMessage(sync0, contactId02, sync1, contactId2);

			// wait for request to arrive
			eventWaiter.await(TIMEOUT, 2);
			assertTrue(listener1.requestReceived);

			// sync responses
			deliverMessage(sync1, contactId1, sync0, contactId01);
			deliverMessage(sync1, contactId2, sync0, contactId02);

			// wait for two responses to arrive
			eventWaiter.await(TIMEOUT, 2);
			assertTrue(listener0.response1Received);
			assertTrue(listener0.response2Received);

			// sync forwarded responses to introducees
			deliverMessage(sync0, contactId01, sync1, contactId1);
			deliverMessage(sync0, contactId02, sync1, contactId2);

			// wait for "both" introducees to abort session
			eventWaiter.await(TIMEOUT, 2);
			assertTrue(listener1.aborted);

			// sync abort message
			deliverMessage(sync1, contactId1, sync0, contactId01);
			deliverMessage(sync1, contactId2, sync0, contactId02);

			// wait for introducer to abort session (gets event twice)
			eventWaiter.await(TIMEOUT, 2);
			assertTrue(listener0.aborted);

			assertFalse(contactManager1
					.contactExists(author1.getId(), author2.getId()));
			assertFalse(contactManager1
					.contactExists(author2.getId(), author1.getId()));

			assertEquals(2, introductionManager0.getIntroductionMessages(
					contactId1).size());
			assertEquals(2, introductionManager0.getIntroductionMessages(
					contactId2).size());
			assertEquals(2, introductionManager1.getIntroductionMessages(
					contactId01).size());
			assertEquals(2, introductionManager1.getIntroductionMessages(
					contactId02).size());
		} finally {
			stopLifecycles();
		}
	}

	// TODO add a test for faking responses when #256 is implemented

	@After
	public void tearDown() throws InterruptedException {
		TestUtils.deleteTestDirectory(testDir);
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

	private void addTransportProperties() throws DbException {
		TransportPropertyManager tpm0 = t0.getTransportPropertyManager();
		TransportPropertyManager tpm1 = t1.getTransportPropertyManager();
		TransportPropertyManager tpm2 = t2.getTransportPropertyManager();

		TransportProperties tp = new TransportProperties(
				Collections.singletonMap("key", "value"));
		tpm0.mergeLocalProperties(TRANSPORT_ID, tp);
		tpm1.mergeLocalProperties(TRANSPORT_ID, tp);
		tpm2.mergeLocalProperties(TRANSPORT_ID, tp);
	}

	private void addDefaultIdentities() throws DbException {
		author0 = authorFactory.createLocalAuthor(INTRODUCER,
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
				TestUtils.getRandomBytes(123));
		identityManager0.addLocalAuthor(author0);
		author1 = authorFactory.createLocalAuthor(INTRODUCEE1,
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
				TestUtils.getRandomBytes(123));
		identityManager1.addLocalAuthor(author1);
		author2 = authorFactory.createLocalAuthor(INTRODUCEE2,
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
				TestUtils.getRandomBytes(123));
		identityManager2.addLocalAuthor(author2);
	}

	private void deliverMessage(SyncSessionFactory fromSync, ContactId fromId,
			SyncSessionFactory toSync, ContactId toId)
			throws IOException, TimeoutException {
		deliverMessage(fromSync, fromId, toSync, toId, null);
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

	private void assertDefaultUiMessages() throws DbException {
		assertEquals(2, introductionManager0.getIntroductionMessages(
				contactId1).size());
		assertEquals(2, introductionManager0.getIntroductionMessages(
				contactId2).size());
		assertEquals(2, introductionManager1.getIntroductionMessages(
				contactId0).size());
		assertEquals(2, introductionManager2.getIntroductionMessages(
				contactId0).size());
	}

	private class IntroduceeListener implements EventListener {

		public volatile boolean requestReceived = false;
		public volatile boolean succeeded = false;
		public volatile boolean aborted = false;

		private final int introducee;
		private final boolean accept;

		IntroduceeListener(int introducee, boolean accept) {
			this.introducee = introducee;
			this.accept = accept;
		}

		public void eventOccurred(Event e) {
			if (e instanceof MessageValidatedEvent) {
				MessageValidatedEvent event = (MessageValidatedEvent) e;
				if (event.getClientId()
						.equals(introductionManager0.getClientId()) &&
						!event.isLocal()) {
					LOG.info("TEST: Introducee" + introducee +
							" received message in group " +
							((MessageValidatedEvent) e).getMessage()
									.getGroupId().hashCode());
					msgWaiter.resume();
				}
			} else if (e instanceof IntroductionRequestReceivedEvent) {
				IntroductionRequestReceivedEvent introEvent =
						((IntroductionRequestReceivedEvent) e);
				requestReceived = true;
				IntroductionRequest ir = introEvent.getIntroductionRequest();
				ContactId contactId = introEvent.getContactId();
				SessionId sessionId = ir.getSessionId();
				long time = clock.currentTimeMillis();
				try {
					if (introducee == 1) {
						if (accept) {
							introductionManager1
									.acceptIntroduction(contactId, sessionId,
											time);
						} else {
							introductionManager1
									.declineIntroduction(contactId, sessionId,
											time);
						}
					} else if (introducee == 2) {
						if (accept) {
							introductionManager2
									.acceptIntroduction(contactId, sessionId,
											time);
						} else {
							introductionManager2
									.declineIntroduction(contactId, sessionId,
											time);
						}
					}
				} catch (DbException exception) {
					eventWaiter.rethrow(exception);
				} catch (IOException exception) {
					eventWaiter.rethrow(exception);
				} finally {
					eventWaiter.resume();
				}
			} else if (e instanceof IntroductionSucceededEvent) {
				succeeded = true;
				Contact contact = ((IntroductionSucceededEvent) e).getContact();
				eventWaiter.assertFalse(contact.getId().equals(contactId0));
				eventWaiter.assertTrue(contact.isActive());
				eventWaiter.resume();
			} else if (e instanceof IntroductionAbortedEvent) {
				aborted = true;
				eventWaiter.resume();
			}
		}
	}

	private class IntroducerListener implements EventListener {

		public volatile boolean response1Received = false;
		public volatile boolean response2Received = false;
		public volatile boolean aborted = false;

		public void eventOccurred(Event e) {
			if (e instanceof MessageValidatedEvent) {
				MessageValidatedEvent event = (MessageValidatedEvent) e;
				if (event.getClientId()
						.equals(introductionManager0.getClientId()) &&
						!event.isLocal()) {
					LOG.info("TEST: Introducer received message in group " +
							((MessageValidatedEvent) e).getMessage()
									.getGroupId().hashCode());
					msgWaiter.resume();
				}
			} else if (e instanceof IntroductionResponseReceivedEvent) {
				ContactId c =
						((IntroductionResponseReceivedEvent) e).getContactId();
				try {
					if (c.equals(contactId1)) {
						response1Received = true;
					} else if (c.equals(contactId2)) {
						response2Received = true;
					}
				} finally {
					eventWaiter.resume();
				}
			} else if (e instanceof IntroductionAbortedEvent) {
				aborted = true;
				eventWaiter.resume();
			}
		}
	}

	private void injectEagerSingletons(
			IntroductionIntegrationTestComponent component) {

		component.inject(new LifecycleModule.EagerSingletons());
		component.inject(new LifecycleModule.EagerSingletons());
		component.inject(new IntroductionModule.EagerSingletons());
		component.inject(new CryptoModule.EagerSingletons());
		component.inject(new ContactModule.EagerSingletons());
		component.inject(new TransportModule.EagerSingletons());
		component.inject(new SyncModule.EagerSingletons());
		component.inject(new PropertiesModule.EagerSingletons());
	}

}
