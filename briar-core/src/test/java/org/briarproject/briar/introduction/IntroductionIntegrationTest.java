package org.briarproject.briar.introduction;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseModule;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.introduction.IntroductionMessage;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.event.IntroductionAbortedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionRequestReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionResponseReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionSucceededEvent;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.junit.Before;
import org.junit.Test;

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

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.test.TestPluginConfigModule.TRANSPORT_ID;
import static org.briarproject.briar.api.client.MessageQueueManager.QUEUE_STATE_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NONCE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.briarproject.briar.introduction.IntroduceeManager.SIGNING_LABEL_RESPONSE;
import static org.briarproject.briar.test.BriarTestUtils.assertGroupCount;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IntroductionIntegrationTest
		extends BriarIntegrationTest<IntroductionIntegrationTestComponent> {

	@Inject
	IntroductionGroupFactory introductionGroupFactory;

	// objects accessed from background threads need to be volatile
	private volatile IntroductionManager introductionManager0;
	private volatile IntroductionManager introductionManager1;
	private volatile IntroductionManager introductionManager2;
	private volatile Waiter eventWaiter;

	private IntroducerListener listener0;
	private IntroduceeListener listener1;
	private IntroduceeListener listener2;

	private static final Logger LOG =
			Logger.getLogger(IntroductionIntegrationTest.class.getName());

	interface StateVisitor {
		boolean visit(BdfDictionary response);
	}

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		introductionManager0 = c0.getIntroductionManager();
		introductionManager1 = c1.getIntroductionManager();
		introductionManager2 = c2.getIntroductionManager();

		// initialize waiter fresh for each test
		eventWaiter = new Waiter();

		addTransportProperties();
	}

	@Override
	protected void createComponents() {
		IntroductionIntegrationTestComponent component =
				DaggerIntroductionIntegrationTestComponent.builder().build();
		component.inject(this);

		c0 = DaggerIntroductionIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(c0);

		c1 = DaggerIntroductionIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(c1);

		c2 = DaggerIntroductionIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t2Dir)).build();
		injectEagerSingletons(c2);
	}

	@Test
	public void testIntroductionSession() throws Exception {
		addListeners(true, true);

		// make introduction
		long time = clock.currentTimeMillis();
		Contact introducee1 = contact1From0;
		Contact introducee2 = contact2From0;
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
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);

		// sync second request message
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener2.requestReceived);
		assertGroupCount(messageTracker2, g2.getId(), 2, 1);

		// sync first response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response1Received);
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);

		// sync second response
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);
		assertGroupCount(messageTracker0, g2.getId(), 2, 1);

		// sync forwarded responses to introducees
		sync0To1(1, true);
		sync0To2(1, true);
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);
		assertGroupCount(messageTracker2, g2.getId(), 2, 1);

		// sync first ACK and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// sync second ACK and its forward
		sync2To0(1, true);
		sync0To1(1, true);

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
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);
		assertGroupCount(messageTracker2, g2.getId(), 2, 1);
	}

	@Test
	public void testIntroductionSessionFirstDecline() throws Exception {
		addListeners(false, true);

		// make introduction
		long time = clock.currentTimeMillis();
		Contact introducee1 = contact1From0;
		Contact introducee2 = contact2From0;
		introductionManager0
				.makeIntroduction(introducee1, introducee2, null, time);

		// sync request messages
		sync0To1(1, true);
		sync0To2(1, true);

		// wait for requests to arrive
		eventWaiter.await(TIMEOUT, 2);
		assertTrue(listener1.requestReceived);
		assertTrue(listener2.requestReceived);

		// sync first response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response1Received);

		// sync second response
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);

		// sync first forwarded response
		sync0To2(1, true);

		// note how the introducer does not forward the second response,
		// because after the first decline the protocol finished

		assertFalse(listener1.succeeded);
		assertFalse(listener2.succeeded);

		assertFalse(contactManager1
				.contactExists(author2.getId(), author1.getId()));
		assertFalse(contactManager2
				.contactExists(author1.getId(), author2.getId()));

		Group g1 = introductionGroupFactory
				.createIntroductionGroup(introducee1);
		Group g2 = introductionGroupFactory
				.createIntroductionGroup(introducee2);
		assertEquals(2,
				introductionManager0.getIntroductionMessages(contactId1From0)
						.size());
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);
		assertEquals(2,
				introductionManager0.getIntroductionMessages(contactId2From0)
						.size());
		assertGroupCount(messageTracker0, g2.getId(), 2, 1);
		assertEquals(2,
				introductionManager1.getIntroductionMessages(contactId0From1)
						.size());
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);
		// introducee2 should also have the decline response of introducee1
		assertEquals(3,
				introductionManager2.getIntroductionMessages(contactId0From2)
						.size());
		assertGroupCount(messageTracker2, g2.getId(), 3, 2);
	}

	@Test
	public void testIntroductionSessionSecondDecline() throws Exception {
		addListeners(true, false);

		// make introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// sync request messages
		sync0To1(1, true);
		sync0To2(1, true);

		// wait for requests to arrive
		eventWaiter.await(TIMEOUT, 2);
		assertTrue(listener1.requestReceived);
		assertTrue(listener2.requestReceived);

		// sync first response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response1Received);

		// sync second response
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);

		// sync both forwarded response
		sync0To2(1, true);
		sync0To1(1, true);

		assertFalse(contactManager1
				.contactExists(author2.getId(), author1.getId()));
		assertFalse(contactManager2
				.contactExists(author1.getId(), author2.getId()));

		assertEquals(2,
				introductionManager0.getIntroductionMessages(contactId1From0)
						.size());
		assertEquals(2,
				introductionManager0.getIntroductionMessages(contactId2From0)
						.size());
		// introducee1 also sees the decline response from introducee2
		assertEquals(3,
				introductionManager1.getIntroductionMessages(contactId0From1)
						.size());
		assertEquals(2,
				introductionManager2.getIntroductionMessages(contactId0From2)
						.size());
	}

	@Test
	public void testIntroductionSessionDelayedFirstDecline() throws Exception {
		addListeners(false, false);

		// make introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// sync request messages
		sync0To1(1, true);
		sync0To2(1, true);

		// wait for requests to arrive
		eventWaiter.await(TIMEOUT, 2);
		assertTrue(listener1.requestReceived);
		assertTrue(listener2.requestReceived);

		// sync first response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response1Received);

		// sync fake transport properties back to 1, so Message ACK can arrive
		// and the assertDefaultUiMessages() check at the end will not fail
		TransportProperties tp = new TransportProperties(
				Collections.singletonMap("key", "value"));
		c0.getTransportPropertyManager()
				.mergeLocalProperties(new TransportId("fake"), tp);
		sync0To1(1, true);

		// sync second response
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);

		// sync first forwarded response
		sync0To2(1, true);

		// note how the second response will not be forwarded anymore

		assertFalse(contactManager1
				.contactExists(author2.getId(), author1.getId()));
		assertFalse(contactManager2
				.contactExists(author1.getId(), author2.getId()));

		// since introducee2 was already in FINISHED state when
		// introducee1's response arrived, she ignores and deletes it
		assertDefaultUiMessages();
	}

	@Test
	public void testResponseAndAckInOneSession() throws Exception {
		addListeners(true, true);

		// make introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!", time);

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// sync first response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response1Received);

		// don't let 2 answer the request right away
		// to have the response arrive first
		listener2.answerRequests = false;

		// sync second request message and first response
		sync0To2(2, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener2.requestReceived);

		// answer request manually
		introductionManager2
				.acceptIntroduction(contactId0From2, listener2.sessionId, time);

		// sync second response and ACK and make sure there is no abort
		sync2To0(2, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);
		assertFalse(listener0.aborted);
	}

	@Test
	public void testIntroductionToSameContact() throws Exception {
		addListeners(true, false);

		// make introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact1From0, null, time);

		// sync request messages
		sync0To1(1, false);

		// we should not get any event, because the request will be discarded
		assertFalse(listener1.requestReceived);

		// make really sure we don't have that request
		assertTrue(introductionManager1.getIntroductionMessages(contactId0From1)
				.isEmpty());
	}

	@Test
	public void testSessionIdReuse() throws Exception {
		addListeners(true, true);

		// make introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!", time);

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// get SessionId
		List<IntroductionMessage> list = new ArrayList<IntroductionMessage>(
				introductionManager1.getIntroductionMessages(contactId0From1));
		assertEquals(2, list.size());
		assertTrue(list.get(0) instanceof IntroductionRequest);
		IntroductionRequest msg = (IntroductionRequest) list.get(0);
		SessionId sessionId = msg.getSessionId();

		// get contact group
		Group group =
				introductionGroupFactory.createIntroductionGroup(contact1From0);

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
		MessageSender sender0 = c0.getMessageSender();
		Transaction txn = db0.startTransaction(false);
		try {
			sender0.sendMessage(txn, d);
			db0.commitTransaction(txn);
		} finally {
			db0.endTransaction(txn);
		}

		// actually send message
		sync0To1(1, false);

		// make sure it does not arrive
		assertFalse(listener1.requestReceived);
	}

	@Test
	public void testIntroducerRemovedCleanup() throws Exception {
		addListeners(true, true);

		// make introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!", time);

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// get database and local group for introducee
		Group group1 = introductionGroupFactory.createLocalGroup();

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
		contactManager1.removeContact(contactId0From1);

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
	}

	@Test
	public void testIntroduceesRemovedCleanup() throws Exception {
		addListeners(true, true);

		// make introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!", time);

		// sync first request message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);

		// get database and local group for introducee
		Group group1 = introductionGroupFactory.createLocalGroup();

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
		contactManager0.removeContact(contactId1From0);

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
		contactManager0.removeContact(contactId2From0);

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
	}

	private void testModifiedResponse(StateVisitor visitor)
			throws Exception {
		addListeners(true, true);

		// make introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!", time);

		// sync request messages
		sync0To1(1, true);
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 2);

		// sync first response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// get response to be forwarded
		ClientHelper ch = c0.getClientHelper(); // need 0's ClientHelper here
		Entry<MessageId, BdfDictionary> resp =
				getMessageFor(ch, contact2From0, TYPE_RESPONSE);
		MessageId responseId = resp.getKey();
		BdfDictionary response = resp.getValue();

		// adapt outgoing message queue to removed message
		Group g2 = introductionGroupFactory
				.createIntroductionGroup(contact2From0);
		decreaseOutgoingMessageCounter(ch, g2.getId(), 1);

		// allow visitor to modify response
		boolean earlyAbort = visitor.visit(response);

		// replace original response with modified one
		MessageSender sender0 = c0.getMessageSender();
		Transaction txn = db0.startTransaction(false);
		try {
			db0.deleteMessage(txn, responseId);
			sender0.sendMessage(txn, response);
			db0.commitTransaction(txn);
		} finally {
			db0.endTransaction(txn);
		}

		// sync second response
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// sync forwarded responses to introducees
		sync0To1(1, true);
		sync0To2(1, true);

		// sync first ACK and forward it
		sync1To0(1, true);
		sync0To2(1, true);

		// introducee2 should have detected the fake now
		// and deleted introducee1 again
		Collection<Contact> contacts2;
		txn = db2.startTransaction(true);
		try {
			contacts2 = db2.getContacts(txn);
			db2.commitTransaction(txn);
		} finally {
			db2.endTransaction(txn);
		}
		assertEquals(1, contacts2.size());

		// sync introducee2's ack and following abort
		sync2To0(2, true);

		// ensure introducer got the abort
		assertTrue(listener0.aborted);

		// sync abort messages to introducees
		sync0To1(2, true);
		sync0To2(1, true);

		if (earlyAbort) {
			assertTrue(listener1.aborted);
			assertTrue(listener2.aborted);
		} else {
			assertTrue(listener2.aborted);
			// when aborted late, introducee1 keeps the contact,
			// so introducer can not make contacts disappear by aborting
			Collection<Contact> contacts1;
			txn = db1.startTransaction(true);
			try {
				contacts1 = db1.getContacts(txn);
				db1.commitTransaction(txn);
			} finally {
				db1.endTransaction(txn);
			}
			assertEquals(2, contacts1.size());
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
		MessageSender messageSender = c2.getMessageSender();
		TransportPropertyManager tpManager = c2.getTransportPropertyManager();
		IntroduceeManager manager2 =
				new IntroduceeManager(messageSender, db2, clientHelper, clock,
						crypto, tpManager, authorFactory, contactManager2,
						identityManager2, introductionGroupFactory);

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
		byte[] sig1 = crypto.sign(SIGNING_LABEL_RESPONSE, nonce1,
				keyPair1.getPrivate().getEncoded());

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
		} catch (GeneralSecurityException e) {
			// expected
		}
	}

	private void addTransportProperties()
			throws DbException, IOException, TimeoutException {
		TransportPropertyManager tpm0 = c0.getTransportPropertyManager();
		TransportPropertyManager tpm1 = c1.getTransportPropertyManager();
		TransportPropertyManager tpm2 = c2.getTransportPropertyManager();
		TransportProperties tp = new TransportProperties(
				Collections.singletonMap("key", "value"));

		tpm0.mergeLocalProperties(TRANSPORT_ID, tp);
		sync0To1(1, true);
		sync0To2(1, true);

		tpm1.mergeLocalProperties(TRANSPORT_ID, tp);
		sync1To0(1, true);

		tpm2.mergeLocalProperties(TRANSPORT_ID, tp);
		sync2To0(1, true);
	}

	private void assertDefaultUiMessages() throws DbException {
		Collection<IntroductionMessage> messages =
				introductionManager0.getIntroductionMessages(contactId1From0);
		assertEquals(2, messages.size());
		assertMessagesAreAcked(messages);

		messages = introductionManager0.getIntroductionMessages(
				contactId2From0);
		assertEquals(2, messages.size());
		assertMessagesAreAcked(messages);

		messages = introductionManager1.getIntroductionMessages(
				contactId0From1);
		assertEquals(2, messages.size());
		assertMessagesAreAcked(messages);

		messages = introductionManager2.getIntroductionMessages(
				contactId0From2);
		assertEquals(2, messages.size());
		assertMessagesAreAcked(messages);
	}

	private void assertMessagesAreAcked(
			Collection<IntroductionMessage> messages) {
		for (IntroductionMessage msg : messages) {
			if (msg.isLocal()) assertTrue(msg.isSeen());
		}
	}

	private void addListeners(boolean accept1, boolean accept2) {
		// listen to events
		listener0 = new IntroducerListener();
		c0.getEventBus().addListener(listener0);
		listener1 = new IntroduceeListener(1, accept1);
		c1.getEventBus().addListener(listener1);
		listener2 = new IntroduceeListener(2, accept2);
		c2.getEventBus().addListener(listener2);
	}

	@MethodsNotNullByDefault
	@ParametersNotNullByDefault
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
			if (e instanceof IntroductionRequestReceivedEvent) {
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
				} catch (DbException exception) {
					eventWaiter.rethrow(exception);
				} catch (FormatException exception) {
					eventWaiter.rethrow(exception);
				} finally {
					eventWaiter.resume();
				}
			} else if (e instanceof IntroductionSucceededEvent) {
				succeeded = true;
				Contact contact = ((IntroductionSucceededEvent) e).getContact();
				eventWaiter
						.assertFalse(contact.getId().equals(contactId0From1));
				eventWaiter.assertTrue(contact.isActive());
				eventWaiter.resume();
			} else if (e instanceof IntroductionAbortedEvent) {
				aborted = true;
				eventWaiter.resume();
			}
		}
	}

	@NotNullByDefault
	private class IntroducerListener implements EventListener {

		private volatile boolean response1Received = false;
		private volatile boolean response2Received = false;
		private volatile boolean aborted = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof IntroductionResponseReceivedEvent) {
				ContactId c =
						((IntroductionResponseReceivedEvent) e)
								.getContactId();
				if (c.equals(contactId1From0)) {
					response1Received = true;
				} else if (c.equals(contactId2From0)) {
					response2Received = true;
				}
				eventWaiter.resume();
			} else if (e instanceof IntroductionAbortedEvent) {
				aborted = true;
				eventWaiter.resume();
			}
		}

	}

	private void decreaseOutgoingMessageCounter(ClientHelper ch, GroupId g,
			int num) throws FormatException, DbException {
		BdfDictionary gD = ch.getGroupMetadataAsDictionary(g);
		LOG.warning(gD.toString());
		BdfDictionary queue = gD.getDictionary(QUEUE_STATE_KEY);
		queue.put("nextOut", queue.getLong("nextOut") - num);
		gD.put(QUEUE_STATE_KEY, queue);
		ch.mergeGroupMetadata(g, gD);
	}

	private Entry<MessageId, BdfDictionary> getMessageFor(ClientHelper ch,
			Contact contact, long type) throws FormatException, DbException {
		Entry<MessageId, BdfDictionary> response = null;
		Group g = introductionGroupFactory
				.createIntroductionGroup(contact);
		Map<MessageId, BdfDictionary> map =
				ch.getMessageMetadataAsDictionary(g.getId());
		for (Entry<MessageId, BdfDictionary> entry : map.entrySet()) {
			if (entry.getValue().getLong(TYPE) == type) {
				response = entry;
			}
		}
		assertTrue(response != null);
		return response;
	}

}
