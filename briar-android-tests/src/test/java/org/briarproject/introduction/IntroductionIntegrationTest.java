package org.briarproject.introduction;

import android.support.annotation.Nullable;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.BriarIntegrationTest;
import org.briarproject.TestDatabaseModule;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.IntroductionAbortedEvent;
import org.briarproject.api.event.IntroductionRequestReceivedEvent;
import org.briarproject.api.event.IntroductionResponseReceivedEvent;
import org.briarproject.api.event.IntroductionSucceededEvent;
import org.briarproject.api.event.MessageStateChangedEvent;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.introduction.IntroductionMessage;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.sync.ValidationManager.State;
import org.briarproject.api.system.Clock;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.system.SystemModule;
import org.briarproject.transport.TransportModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.TestPluginsModule.MAX_LATENCY;
import static org.briarproject.TestPluginsModule.TRANSPORT_ID;
import static org.briarproject.api.clients.MessageQueueManager.QUEUE_STATE_KEY;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.api.introduction.IntroductionConstants.MAC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NONCE;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IntroductionIntegrationTest extends BriarIntegrationTest {

	private LifecycleManager lifecycleManager0, lifecycleManager1,
			lifecycleManager2;
	private SyncSessionFactory sync0, sync1, sync2;
	private ContactManager contactManager0, contactManager1, contactManager2;
	private MessageTracker messageTracker0, messageTracker1, messageTracker2;
	private ContactId contactId0, contactId1, contactId2;
	private IdentityManager identityManager0, identityManager1,
			identityManager2;
	private LocalAuthor author0, author1, author2;

	@Inject
	Clock clock;
	@Inject
	CryptoComponent crypto;
	@Inject
	AuthorFactory authorFactory;
	@Inject
	IntroductionGroupFactory introductionGroupFactory;

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
	private IntroducerListener listener0;
	private IntroduceeListener listener1;
	private IntroduceeListener listener2;

	private static final Logger LOG =
			Logger.getLogger(IntroductionIntegrationTest.class.getName());

	private IntroductionIntegrationTestComponent t0, t1, t2;

	interface StateVisitor {
		boolean visit(BdfDictionary response);
	}

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
		messageTracker0 = t0.getMessageTracker();
		messageTracker1 = t1.getMessageTracker();
		messageTracker2 = t2.getMessageTracker();
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
			getDefaultIdentities();
			addDefaultContacts();
			addListeners(true, true);
			addTransportProperties();

			// make introduction
			long time = clock.currentTimeMillis();
			Contact introducee1 = contactManager0.getContact(contactId1);
			Contact introducee2 = contactManager0.getContact(contactId2);
			introductionManager0
					.makeIntroduction(introducee1, introducee2, "Hi!", time);

			// check that messages are tracked properly
			Group g1 = introductionGroupFactory
					.createIntroductionGroup(introducee1);
			Group g2 = introductionGroupFactory
					.createIntroductionGroup(introducee2);
			assertGroupCount(messageTracker0, g1.getId(), 1, 0, time);
			assertGroupCount(messageTracker0, g2.getId(), 1, 0, time);

			// sync first request message
			deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);
			assertGroupCount(messageTracker1, g1.getId(), 2, 1);

			// sync second request message
			deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener2.requestReceived);
			assertGroupCount(messageTracker2, g2.getId(), 2, 1);

			// sync first response
			deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response1Received);
			assertGroupCount(messageTracker0, g1.getId(), 2, 1);

			// sync second response
			deliverMessage(sync2, contactId2, sync0, contactId0, "2 to 0");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.response2Received);
			assertGroupCount(messageTracker0, g2.getId(), 2, 1);

			// sync forwarded responses to introducees
			deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");
			deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");
			assertGroupCount(messageTracker1, g1.getId(), 3, 2);
			assertGroupCount(messageTracker2, g2.getId(), 3, 2);

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

			// make sure that introduced contacts are not verified
			for (Contact c : contactManager1.getActiveContacts()) {
				if (c.getAuthor().equals(author2)) {
					assertFalse(c.isVerified());
				}
			}
			for (Contact c : contactManager2.getActiveContacts()) {
				if (c.getAuthor().equals(author1)) {
					assertFalse(c.isVerified());
				}
			}

			assertDefaultUiMessages();
			assertGroupCount(messageTracker0, g1.getId(), 2, 1);
			assertGroupCount(messageTracker0, g2.getId(), 2, 1);
			assertGroupCount(messageTracker1, g1.getId(), 3, 2);
			assertGroupCount(messageTracker2, g2.getId(), 3, 2);
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testIntroductionSessionFirstDecline() throws Exception {
		startLifecycles();
		try {
			getDefaultIdentities();
			addDefaultContacts();
			addListeners(false, true);
			addTransportProperties();

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
			getDefaultIdentities();
			addDefaultContacts();
			addListeners(true, false);
			addTransportProperties();

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
			getDefaultIdentities();
			addDefaultContacts();
			addListeners(false, false);
			addTransportProperties();

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
	public void testResponseAndAckInOneSession() throws Exception {
		startLifecycles();

		getDefaultIdentities();
		addDefaultContacts();
		addListeners(true, true);
		addTransportProperties();

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

		// sync first response
		deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response1Received);

		// don't let 2 answer the request right away
		// to have the response arrive first
		listener2.answerRequests = false;

		// sync second request message and first response
		deliverMessage(sync0, contactId0, sync2, contactId2, 2, "0 to 2");
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener2.requestReceived);

		// answer request manually
		introductionManager2
				.acceptIntroduction(contactId0, listener2.sessionId, time);

		// sync second response and ACK and make sure there is no abort
		deliverMessage(sync2, contactId2, sync0, contactId0, 2, "2 to 0");
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);
		assertFalse(listener0.aborted);

		stopLifecycles();
	}

	@Test
	public void testIntroductionToSameContact() throws Exception {
		startLifecycles();
		try {
			getDefaultIdentities();
			addDefaultContacts();
			addListeners(true, false);
			addTransportProperties();

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
	public void testSessionIdReuse() throws Exception {
		startLifecycles();
		try {
			getDefaultIdentities();
			addDefaultContacts();
			addListeners(true, true);
			addTransportProperties();

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

			// get SessionId
			List<IntroductionMessage> list = new ArrayList<>(
					introductionManager1.getIntroductionMessages(contactId0));
			assertEquals(2, list.size());
			assertTrue(list.get(0) instanceof IntroductionRequest);
			IntroductionRequest msg = (IntroductionRequest) list.get(0);
			SessionId sessionId = msg.getSessionId();

			// get contact group
			IntroductionGroupFactory groupFactory =
					t0.getIntroductionGroupFactory();
			Group group = groupFactory.createIntroductionGroup(introducee1);

			// create new message with same SessionId
			BdfDictionary d = BdfDictionary.of(
					new BdfEntry(TYPE, TYPE_REQUEST),
					new BdfEntry(SESSION_ID, sessionId),
					new BdfEntry(GROUP_ID, group.getId()),
					new BdfEntry(NAME, TestUtils.getRandomString(42)),
					new BdfEntry(PUBLIC_KEY,
							TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH))
			);

			// reset request received state
			listener1.requestReceived = false;

			// add the message to the queue
			DatabaseComponent db0 = t0.getDatabaseComponent();
			MessageSender sender0 = t0.getMessageSender();
			Transaction txn = db0.startTransaction(false);
			try {
				sender0.sendMessage(txn, d);
				db0.commitTransaction(txn);
			} finally {
				db0.endTransaction(txn);
			}

			// actually send message
			deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");

			// make sure it does not arrive
			assertFalse(listener1.requestReceived);
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testIntroducerRemovedCleanup() throws Exception {
		startLifecycles();
		try {
			getDefaultIdentities();
			addDefaultContacts();
			addListeners(true, true);
			addTransportProperties();

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

			// get database and local group for introducee
			DatabaseComponent db1 = t1.getDatabaseComponent();
			IntroductionGroupFactory groupFactory1 =
					t1.getIntroductionGroupFactory();
			Group group1 = groupFactory1.createLocalGroup();

			// get local session state messages
			Map<MessageId, Metadata> map;
			Transaction txn = db1.startTransaction(false);
			try {
				map = db1.getMessageMetadata(txn, group1.getId());
				db1.commitTransaction(txn);
			} finally {
				db1.endTransaction(txn);
			}
			// check that we have one session state
			assertEquals(1, map.size());

			// introducee1 removes introducer
			contactManager1.removeContact(contactId0);

			// get local session state messages again
			txn = db1.startTransaction(false);
			try {
				map = db1.getMessageMetadata(txn, group1.getId());
				db1.commitTransaction(txn);
			} finally {
				db1.endTransaction(txn);
			}
			// make sure local state got deleted
			assertEquals(0, map.size());
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testIntroduceesRemovedCleanup() throws Exception {
		startLifecycles();
		try {
			getDefaultIdentities();
			addDefaultContacts();
			addListeners(true, true);
			addTransportProperties();

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

			// get database and local group for introducee
			DatabaseComponent db0 = t0.getDatabaseComponent();
			IntroductionGroupFactory groupFactory0 =
					t0.getIntroductionGroupFactory();
			Group group1 = groupFactory0.createLocalGroup();

			// get local session state messages
			Map<MessageId, Metadata> map;
			Transaction txn = db0.startTransaction(false);
			try {
				map = db0.getMessageMetadata(txn, group1.getId());
				db0.commitTransaction(txn);
			} finally {
				db0.endTransaction(txn);
			}
			// check that we have one session state
			assertEquals(1, map.size());

			// introducer removes introducee1
			contactManager0.removeContact(contactId1);

			// get local session state messages again
			txn = db0.startTransaction(false);
			try {
				map = db0.getMessageMetadata(txn, group1.getId());
				db0.commitTransaction(txn);
			} finally {
				db0.endTransaction(txn);
			}
			// make sure local state is still there
			assertEquals(1, map.size());

			// introducer removes other introducee
			contactManager0.removeContact(contactId2);

			// get local session state messages again
			txn = db0.startTransaction(false);
			try {
				map = db0.getMessageMetadata(txn, group1.getId());
				db0.commitTransaction(txn);
			} finally {
				db0.endTransaction(txn);
			}
			// make sure local state is gone now
			assertEquals(0, map.size());
		} finally {
			stopLifecycles();
		}
	}

	private void testModifiedResponse(StateVisitor visitor)
			throws Exception {
		startLifecycles();
		try {
			getDefaultIdentities();
			addDefaultContacts();
			addListeners(true, true);
			addTransportProperties();

			// make introduction
			long time = clock.currentTimeMillis();
			Contact introducee1 = contactManager0.getContact(contactId1);
			Contact introducee2 = contactManager0.getContact(contactId2);
			introductionManager0
					.makeIntroduction(introducee1, introducee2, "Hi!", time);

			// sync request messages
			deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");
			deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");
			eventWaiter.await(TIMEOUT, 2);

			// sync first response
			deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
			eventWaiter.await(TIMEOUT, 1);

			// get response to be forwarded
			Entry<MessageId, BdfDictionary> resp =
					getMessageFor(introducee2, TYPE_RESPONSE);
			MessageId responseId = resp.getKey();
			BdfDictionary response = resp.getValue();

			// adapt outgoing message queue to removed message
			ClientHelper clientHelper0 = t0.getClientHelper();
			Group g2 = introductionGroupFactory
					.createIntroductionGroup(introducee2);
			decreaseOutgoingMessageCounter(clientHelper0, g2.getId(), 1);

			// allow visitor to modify response
			boolean earlyAbort = visitor.visit(response);

			// replace original response with modified one
			MessageSender sender0 = t0.getMessageSender();
			DatabaseComponent db0 = t0.getDatabaseComponent();
			Transaction txn = db0.startTransaction(false);
			try {
				db0.deleteMessage(txn, responseId);
				sender0.sendMessage(txn, response);
				db0.commitTransaction(txn);
			} finally {
				db0.endTransaction(txn);
			}

			// sync second response
			deliverMessage(sync2, contactId2, sync0, contactId0, "2 to 0");
			eventWaiter.await(TIMEOUT, 1);

			// sync forwarded responses to introducees
			deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");
			deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");

			// sync first ACK and forward it
			deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");
			deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");

			// introducee2 should have detected the fake now
			// and deleted introducee1 again
			Collection<Contact> contacts2;
			DatabaseComponent db2 = t2.getDatabaseComponent();
			txn = db2.startTransaction(true);
			try {
				contacts2 = db2.getContacts(txn);
				db2.commitTransaction(txn);
			} finally {
				db2.endTransaction(txn);
			}
			assertEquals(1, contacts2.size());

			// sync introducee2's ack and following abort
			deliverMessage(sync2, contactId2, sync0, contactId0, 2, "2 to 0");

			// ensure introducer got the abort
			assertTrue(listener0.aborted);

			// sync abort messages to introducees
			deliverMessage(sync0, contactId0, sync1, contactId1, 2, "0 to 1");
			deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");

			if (earlyAbort) {
				assertTrue(listener1.aborted);
				assertTrue(listener2.aborted);
			} else {
				assertTrue(listener2.aborted);
				// when aborted late, introducee1 keeps the contact,
				// so introducer can not make contacts disappear by aborting
				Collection<Contact> contacts1;
				DatabaseComponent db1 = t1.getDatabaseComponent();
				txn = db1.startTransaction(true);
				try {
					contacts1 = db1.getContacts(txn);
					db1.commitTransaction(txn);
				} finally {
					db1.endTransaction(txn);
				}
				assertEquals(2, contacts1.size());
			}
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testModifiedTransportProperties() throws Exception {
		testModifiedResponse(new StateVisitor() {
			@Override
			public boolean visit(BdfDictionary response) {
				BdfDictionary tp = response.getDictionary(TRANSPORT, null);
				tp.put("fakeId",
						BdfDictionary.of(new BdfEntry("fake", "fake")));
				response.put(TRANSPORT, tp);
				return false;
			}
		});
	}

	@Test
	public void testModifiedTimestamp() throws Exception {
		testModifiedResponse(new StateVisitor() {
			@Override
			public boolean visit(BdfDictionary response) {
				long timestamp = response.getLong(TIME, 0L);
				response.put(TIME, timestamp + 1);
				return false;
			}
		});
	}

	@Test
	public void testModifiedEphemeralPublicKey() throws Exception {
		testModifiedResponse(new StateVisitor() {
			@Override
			public boolean visit(BdfDictionary response) {
				KeyPair keyPair = crypto.generateSignatureKeyPair();
				response.put(E_PUBLIC_KEY, keyPair.getPublic().getEncoded());
				return true;
			}
		});
	}

	@Test
	public void testModifiedEphemeralPublicKeyWithFakeMac()
			throws Exception {
		// initialize a real introducee manager
		MessageSender messageSender = t2.getMessageSender();
		DatabaseComponent db = t2.getDatabaseComponent();
		ClientHelper clientHelper = t2.getClientHelper();
		TransportPropertyManager tpManager = t2.getTransportPropertyManager();
		ContactManager contactManager = t2.getContactManager();
		IdentityManager identityManager = t2.getIdentityManager();
		IntroduceeManager manager2 =
				new IntroduceeManager(messageSender, db, clientHelper, clock,
						crypto, tpManager, authorFactory, contactManager,
						identityManager, introductionGroupFactory);

		// create keys
		KeyPair keyPair1 = crypto.generateSignatureKeyPair();
		KeyPair eKeyPair1 = crypto.generateAgreementKeyPair();
		byte[] ePublicKeyBytes1 = eKeyPair1.getPublic().getEncoded();
		KeyPair eKeyPair2 = crypto.generateAgreementKeyPair();
		byte[] ePublicKeyBytes2 = eKeyPair2.getPublic().getEncoded();

		// Nonce 1
		SecretKey secretKey =
				crypto.deriveMasterSecret(ePublicKeyBytes2, eKeyPair1, true);
		byte[] nonce1 = crypto.deriveSignatureNonce(secretKey, true);

		// Signature 1
		Signature signature = crypto.getSignature();
		signature.initSign(keyPair1.getPrivate());
		signature.update(nonce1);
		byte[] sig1 = signature.sign();

		// MAC 1
		SecretKey macKey1 = crypto.deriveMacKey(secretKey, true);
		BdfDictionary tp1 = BdfDictionary.of(new BdfEntry("fake", "fake"));
		long time1 = clock.currentTimeMillis();
		BdfList toMacList = BdfList.of(keyPair1.getPublic().getEncoded(),
				ePublicKeyBytes1, tp1, time1);
		byte[] toMac = clientHelper.toByteArray(toMacList);
		byte[] mac1 = crypto.mac(macKey1, toMac);

		// create only relevant part of state for introducee2
		BdfDictionary state = new BdfDictionary();
		state.put(PUBLIC_KEY, keyPair1.getPublic().getEncoded());
		state.put(TRANSPORT, tp1);
		state.put(TIME, time1);
		state.put(E_PUBLIC_KEY, ePublicKeyBytes1);
		state.put(MAC, mac1);
		state.put(MAC_KEY, macKey1.getBytes());
		state.put(NONCE, nonce1);
		state.put(SIGNATURE, sig1);

		// MAC and signature verification should pass
		manager2.verifyMac(state);
		manager2.verifySignature(state);

		// replace ephemeral key pair and recalculate matching keys and nonce
		KeyPair eKeyPair1f = crypto.generateAgreementKeyPair();
		byte[] ePublicKeyBytes1f = eKeyPair1f.getPublic().getEncoded();
		secretKey =
				crypto.deriveMasterSecret(ePublicKeyBytes2, eKeyPair1f, true);
		nonce1 = crypto.deriveSignatureNonce(secretKey, true);

		// recalculate MAC
		macKey1 = crypto.deriveMacKey(secretKey, true);
		toMacList = BdfList.of(keyPair1.getPublic().getEncoded(),
				ePublicKeyBytes1f, tp1, time1);
		toMac = clientHelper.toByteArray(toMacList);
		mac1 = crypto.mac(macKey1, toMac);

		// update state with faked information
		state.put(E_PUBLIC_KEY, ePublicKeyBytes1f);
		state.put(MAC, mac1);
		state.put(MAC_KEY, macKey1.getBytes());
		state.put(NONCE, nonce1);

		// MAC verification should still pass
		manager2.verifyMac(state);

		// Signature can not be verified, because we don't have private
		// long-term key to fake it
		try {
			manager2.verifySignature(state);
			fail();
		} catch(GeneralSecurityException e) {
			// expected
		}
	}

	@After
	public void tearDown() throws InterruptedException {
		TestUtils.deleteTestDirectory(testDir);
	}

	private void startLifecycles() throws InterruptedException {
		// Start the lifecycle manager and wait for it to finish
		lifecycleManager0 = t0.getLifecycleManager();
		lifecycleManager1 = t1.getLifecycleManager();
		lifecycleManager2 = t2.getLifecycleManager();
		lifecycleManager0.startServices(INTRODUCER);
		lifecycleManager1.startServices(INTRODUCEE1);
		lifecycleManager2.startServices(INTRODUCEE2);
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

	private void addTransportProperties()
			throws DbException, IOException, TimeoutException {
		TransportPropertyManager tpm0 = t0.getTransportPropertyManager();
		TransportPropertyManager tpm1 = t1.getTransportPropertyManager();
		TransportPropertyManager tpm2 = t2.getTransportPropertyManager();
		TransportProperties tp = new TransportProperties(
				Collections.singletonMap("key", "value"));

		tpm0.mergeLocalProperties(TRANSPORT_ID, tp);
		deliverMessage(sync0, contactId0, sync1, contactId1, "0 to 1");
		deliverMessage(sync0, contactId0, sync2, contactId2, "0 to 2");

		tpm1.mergeLocalProperties(TRANSPORT_ID, tp);
		deliverMessage(sync1, contactId1, sync0, contactId0, "1 to 0");

		tpm2.mergeLocalProperties(TRANSPORT_ID, tp);
		deliverMessage(sync2, contactId2, sync0, contactId0, "2 to 0");
	}

	private void addListeners(boolean accept1, boolean accept2) {
		// listen to events
		listener0 = new IntroducerListener();
		t0.getEventBus().addListener(listener0);
		listener1 = new IntroduceeListener(1, accept1);
		t1.getEventBus().addListener(listener1);
		listener2 = new IntroduceeListener(2, accept2);
		t2.getEventBus().addListener(listener2);
	}

	private void getDefaultIdentities() throws DbException {
		author0 = identityManager0.getLocalAuthor();
		author1 = identityManager1.getLocalAuthor();
		author2 = identityManager2.getLocalAuthor();

	}

	private void addDefaultContacts() throws DbException {
		// Add introducees as contacts
		contactId1 = contactManager0.addContact(author1,
				author0.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
		contactId2 = contactManager0.addContact(author2,
				author0.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
		// Add introducer back
		contactId0 = contactManager1.addContact(author0,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
		ContactId contactId02 = contactManager2.addContact(author0,
				author2.getId(), master, clock.currentTimeMillis(), true,
				true, true
		);
		assertTrue(contactId0.equals(contactId02));
	}

	private void deliverMessage(SyncSessionFactory fromSync, ContactId fromId,
			SyncSessionFactory toSync, ContactId toId, String debug)
			throws IOException, TimeoutException {
		deliverMessage(fromSync, fromId, toSync, toId, 1, debug);
	}

	private void deliverMessage(SyncSessionFactory fromSync, ContactId fromId,
			SyncSessionFactory toSync, ContactId toId)
			throws IOException, TimeoutException {
		deliverMessage(fromSync, fromId, toSync, toId, 1, null);
	}

	private void deliverMessage(SyncSessionFactory fromSync, ContactId fromId,
			SyncSessionFactory toSync, ContactId toId, int num,
			@Nullable String debug)
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

		// wait for [num] message(s) to actually arrive
		msgWaiter.await(TIMEOUT, num);
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

		private volatile boolean requestReceived = false;
		private volatile boolean succeeded = false;
		private volatile boolean aborted = false;
		private volatile boolean answerRequests = true;
		private volatile SessionId sessionId;

		private final int introducee;
		private final boolean accept;

		private IntroduceeListener(int introducee, boolean accept) {
			this.introducee = introducee;
			this.accept = accept;
		}

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent event = (MessageStateChangedEvent) e;
				State s = event.getState();
				if ((s == DELIVERED || s == INVALID) && !event.isLocal()) {
					LOG.info("TEST: Introducee" + introducee +
							" received message");
					msgWaiter.resume();
				}
			} else if (e instanceof IntroductionRequestReceivedEvent) {
				IntroductionRequestReceivedEvent introEvent =
						((IntroductionRequestReceivedEvent) e);
				requestReceived = true;
				IntroductionRequest ir = introEvent.getIntroductionRequest();
				ContactId contactId = introEvent.getContactId();
				sessionId = ir.getSessionId();
				long time = clock.currentTimeMillis();
				try {
					if (introducee == 1 && answerRequests) {
						if (accept) {
							introductionManager1
									.acceptIntroduction(contactId, sessionId,
											time);
						} else {
							introductionManager1
									.declineIntroduction(contactId, sessionId,
											time);
						}
					} else if (introducee == 2 && answerRequests) {
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
				} catch (DbException | IOException exception) {
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

		private volatile boolean response1Received = false;
		private volatile boolean response2Received = false;
		private volatile boolean aborted = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent event = (MessageStateChangedEvent) e;
				if (event.getState() == DELIVERED && !event.isLocal()) {
					LOG.info("TEST: Introducer received message");
					msgWaiter.resume();
				}
			} else if (e instanceof IntroductionResponseReceivedEvent) {
				ContactId c =
						((IntroductionResponseReceivedEvent) e).getContactId();
				if (c.equals(contactId1)) {
					response1Received = true;
				} else if (c.equals(contactId2)) {
					response2Received = true;
				}
				eventWaiter.resume();
			} else if (e instanceof IntroductionAbortedEvent) {
				aborted = true;
				eventWaiter.resume();
			}
		}
	}

	private void decreaseOutgoingMessageCounter(ClientHelper clientHelper,
			GroupId g, int num) throws FormatException, DbException {
		BdfDictionary gD = clientHelper.getGroupMetadataAsDictionary(g);
		LOG.warning(gD.toString());
		BdfDictionary queue = gD.getDictionary(QUEUE_STATE_KEY);
		queue.put("nextOut", queue.getLong("nextOut") - num);
		gD.put(QUEUE_STATE_KEY, queue);
		clientHelper.mergeGroupMetadata(g, gD);
	}

	private Entry<MessageId, BdfDictionary> getMessageFor(Contact contact,
			long type) throws FormatException, DbException {
		Entry<MessageId, BdfDictionary> response = null;
		Group g = introductionGroupFactory
				.createIntroductionGroup(contact);
		ClientHelper clientHelper0 = t0.getClientHelper();
		Map<MessageId, BdfDictionary> map =
				clientHelper0.getMessageMetadataAsDictionary(g.getId());
		for (Entry<MessageId, BdfDictionary> entry : map.entrySet()) {
			if (entry.getValue().getLong(TYPE) == type) {
				response = entry;
			}
		}
		assertTrue(response != null);
		return response;
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
		component.inject(new SystemModule.EagerSingletons());
		component.inject(new PropertiesModule.EagerSingletons());
	}
}
