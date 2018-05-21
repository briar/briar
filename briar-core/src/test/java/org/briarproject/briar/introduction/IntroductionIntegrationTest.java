package org.briarproject.briar.introduction;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseModule;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.introduction.IntroductionMessage;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.introduction.event.IntroductionAbortedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionRequestReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionResponseReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionSucceededEvent;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.test.TestPluginConfigModule.TRANSPORT_ID;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTransportProperties;
import static org.briarproject.bramble.test.TestUtils.getTransportPropertiesMap;
import static org.briarproject.briar.api.introduction.IntroductionManager.CLIENT_ID;
import static org.briarproject.briar.api.introduction.IntroductionManager.MAJOR_VERSION;
import static org.briarproject.briar.introduction.IntroduceeState.AWAIT_RESPONSES;
import static org.briarproject.briar.introduction.IntroduceeState.LOCAL_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.A_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.B_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.START;
import static org.briarproject.briar.introduction.IntroductionConstants.MSG_KEY_MESSAGE_TYPE;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_AUTHOR;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_INTRODUCEE_A;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_INTRODUCEE_B;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_LAST_LOCAL_MESSAGE_ID;
import static org.briarproject.briar.introduction.IntroductionConstants.SESSION_KEY_SESSION_ID;
import static org.briarproject.briar.introduction.MessageType.ACCEPT;
import static org.briarproject.briar.introduction.MessageType.AUTH;
import static org.briarproject.briar.introduction.MessageType.DECLINE;
import static org.briarproject.briar.test.BriarTestUtils.assertGroupCount;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IntroductionIntegrationTest
		extends BriarIntegrationTest<IntroductionIntegrationTestComponent> {

	// objects accessed from background threads need to be volatile
	private volatile IntroductionManager introductionManager0;
	private volatile IntroductionManager introductionManager1;
	private volatile IntroductionManager introductionManager2;
	private volatile Waiter eventWaiter;

	private IntroducerListener listener0;
	private IntroduceeListener listener1;
	private IntroduceeListener listener2;

	interface StateVisitor {
		AcceptMessage visit(AcceptMessage response);
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
		Group g1 = introductionManager0.getContactGroup(introducee1);
		Group g2 = introductionManager0.getContactGroup(introducee2);
		assertGroupCount(messageTracker0, g1.getId(), 1, 0);
		assertGroupCount(messageTracker0, g2.getId(), 1, 0);

		// sync first REQUEST message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);
		assertEquals(introducee2.getAuthor().getName(),
				listener1.getRequest().getName());
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);

		// sync second REQUEST message
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener2.requestReceived);
		assertEquals(introducee1.getAuthor().getName(),
				listener2.getRequest().getName());
		assertGroupCount(messageTracker2, g2.getId(), 2, 1);

		// sync first ACCEPT message
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response1Received);
		assertEquals(introducee2.getAuthor().getName(),
				listener0.getResponse().getName());
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);

		// sync second ACCEPT message
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);
		assertEquals(introducee1.getAuthor().getName(),
				listener0.getResponse().getName());
		assertGroupCount(messageTracker0, g2.getId(), 2, 1);

		// sync forwarded ACCEPT messages to introducees
		sync0To1(1, true);
		sync0To2(1, true);

		// sync first AUTH and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// assert that introducee2 did add the transport keys
		IntroduceeSession session2 = getIntroduceeSession(c2);
		assertNotNull(session2.getTransportKeys());
		assertFalse(session2.getTransportKeys().isEmpty());

		// sync second AUTH and its forward as well as the following ACTIVATE
		sync2To0(2, true);
		sync0To1(2, true);

		// assert that introducee1 really purged the key material
		IntroduceeSession session1 = getIntroduceeSession(c1);
		assertNull(session1.getMasterKey());
		assertNull(session1.getLocal().ephemeralPrivateKey);
		assertNull(session1.getTransportKeys());

		// sync second ACTIVATE and its forward
		sync1To0(1, true);
		sync0To2(1, true);

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

		// assert that introducee is in correct state
		IntroduceeSession introduceeSession = getIntroduceeSession(c1);
		assertEquals(LOCAL_DECLINED, introduceeSession.getState());

		// sync first response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response1Received);

		// assert that introducer is in correct state
		boolean alice = c0.getIntroductionCrypto()
				.isAlice(introducee1.getAuthor().getId(),
						introducee2.getAuthor().getId());
		IntroducerSession introducerSession = getIntroducerSession();
		assertEquals(alice ? A_DECLINED : B_DECLINED,
				introducerSession.getState());

		// assert that the name on the decline event is correct
		assertEquals(introducee2.getAuthor().getName(),
				listener0.getResponse().getName());

		// sync second response
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);

		// assert that introducer now moved to START state
		introducerSession = getIntroducerSession();
		assertEquals(START, introducerSession.getState());

		// sync first forwarded response
		sync0To2(1, true);

		// assert that the name on the decline event is correct
		eventWaiter.await(TIMEOUT, 1);
		assertEquals(introducee1.getAuthor().getName(),
				listener2.getResponse().getName());

		// note how the introducer does not forward the second response,
		// because after the first decline the protocol finished

		assertFalse(listener1.succeeded);
		assertFalse(listener2.succeeded);

		assertFalse(contactManager1
				.contactExists(author2.getId(), author1.getId()));
		assertFalse(contactManager2
				.contactExists(author1.getId(), author2.getId()));

		Group g1 = introductionManager0.getContactGroup(introducee1);
		Group g2 = introductionManager0.getContactGroup(introducee2);
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

		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
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

		// assert that the name on the decline event is correct
		eventWaiter.await(TIMEOUT, 1);
		assertEquals(contact2From0.getAuthor().getName(),
				listener1.getResponse().getName());

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
		assertEquals(3,
				introductionManager1.getIntroductionMessages(contactId0From1)
						.size());
		assertEquals(3,
				introductionManager2.getIntroductionMessages(contactId0From2)
						.size());
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	@Test
	public void testResponseAndAuthInOneSync() throws Exception {
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
		introductionManager2.respondToIntroduction(contactId0From2,
				listener2.sessionId, time, true);

		// sync second response and AUTH
		sync2To0(2, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);

		// Forward AUTH and response
		sync0To1(2, true);

		// Second AUTH and ACTIVATE and forward them
		sync1To0(2, true);
		sync0To2(2, true);

		assertTrue(contactManager1
				.contactExists(author2.getId(), author1.getId()));
		assertTrue(contactManager2
				.contactExists(author1.getId(), author2.getId()));

		assertDefaultUiMessages();
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	/**
	 * When an introducee declines an introduction, the other introducee needs
	 * to respond before returning to the START state, otherwise a subsequent
	 * attempt at introducing the same contacts will fail.
	 */
	@Test
	public void testIntroductionSessionManualDecline() throws Exception {
		addListeners(false, true);
		listener2.answerRequests = false;

		// make introduction
		long time = clock.currentTimeMillis();
		Contact introducee1 = contact1From0;
		Contact introducee2 = contact2From0;
		introductionManager0
				.makeIntroduction(introducee1, introducee2, null, time);

		// sync request messages
		sync0To1(1, true);
		sync0To2(1, true);

		// assert that introducee1 is in correct state
		IntroduceeSession introduceeSession = getIntroduceeSession(c1);
		assertEquals(LOCAL_DECLINED, introduceeSession.getState());

		// sync first response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response1Received);

		// assert that introducer is in correct state
		boolean alice = c0.getIntroductionCrypto()
				.isAlice(introducee1.getAuthor().getId(),
						introducee2.getAuthor().getId());
		IntroducerSession introducerSession = getIntroducerSession();
		assertEquals(alice ? A_DECLINED : B_DECLINED,
				introducerSession.getState());

		// assert that introducee2 is in correct state
		introduceeSession = getIntroduceeSession(c2);
		assertEquals(AWAIT_RESPONSES, introduceeSession.getState());

		// forward first DECLINE
		sync0To2(1, true);

		// assert that the name on the decline event is correct
		eventWaiter.await(TIMEOUT, 1);
		assertEquals(introducee1.getAuthor().getName(),
				listener2.getResponse().getName());

		// assert that introducee2 is in correct state
		introduceeSession = getIntroduceeSession(c2);
		assertEquals(IntroduceeState.REMOTE_DECLINED,
				introduceeSession.getState());

		// answer request manually
		introductionManager2.respondToIntroduction(contactId0From2,
				listener2.sessionId, time, false);

		// now introducee2 should have returned to the START state
		introduceeSession = getIntroduceeSession(c2);
		assertEquals(IntroduceeState.START, introduceeSession.getState());

		// sync second response
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);

		// assert that introducer now moved to START state
		introducerSession = getIntroducerSession();
		assertEquals(START, introducerSession.getState());

		// introducee1 is still waiting for second response
		introduceeSession = getIntroduceeSession(c1);
		assertEquals(LOCAL_DECLINED, introduceeSession.getState());

		// forward second response
		sync0To1(1, true);

		// introducee1 can finally move to the START
		introduceeSession = getIntroduceeSession(c1);
		assertEquals(IntroduceeState.START, introduceeSession.getState());

		Group g1 = introductionManager0.getContactGroup(introducee1);
		Group g2 = introductionManager0.getContactGroup(introducee2);
		assertEquals(2,
				introductionManager0.getIntroductionMessages(contactId1From0)
						.size());
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);
		assertEquals(2,
				introductionManager0.getIntroductionMessages(contactId2From0)
						.size());
		assertGroupCount(messageTracker0, g2.getId(), 2, 1);
		assertEquals(3,
				introductionManager1.getIntroductionMessages(contactId0From1)
						.size());
		assertGroupCount(messageTracker1, g1.getId(), 3, 2);
		assertEquals(3,
				introductionManager2.getIntroductionMessages(contactId0From2)
						.size());
		assertGroupCount(messageTracker2, g2.getId(), 3, 2);

		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
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

		// The message was invalid, so no abort message was sent
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	@Test(expected = ProtocolStateException.class)
	public void testDoubleIntroduction() throws Exception {
		// we can make an introduction
		assertTrue(introductionManager0
				.canIntroduce(contact1From0, contact2From0));

		// make the introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// no more introduction allowed while the existing one is in progress
		assertFalse(introductionManager0
				.canIntroduce(contact1From0, contact2From0));

		// try it anyway and fail
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);
	}

	@Test
	public void testIntroductionToExistingContact() throws Exception {
		// let contact1 and contact2 add each other already
		addContacts1And2();
		assertNotNull(contactId2From1);
		assertNotNull(contactId1From2);

		// both will still accept the introduction
		addListeners(true, true);

		// make the introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// sync REQUEST messages
		sync0To1(1, true);
		sync0To2(1, true);

		// assert that introducees get notified about the existing contact
		IntroductionRequest ir1 =
				getIntroductionRequest(introductionManager1, contactId0From1);
		assertTrue(ir1.contactExists());
		IntroductionRequest ir2 =
				getIntroductionRequest(introductionManager2, contactId0From2);
		assertTrue(ir2.contactExists());

		// sync ACCEPT messages back to introducer
		sync1To0(1, true);
		sync2To0(1, true);

		// sync forwarded ACCEPT messages to introducees
		sync0To1(1, true);
		sync0To2(1, true);

		// sync first AUTH and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// sync second AUTH and its forward as well as the following ACTIVATE
		sync2To0(2, true);
		sync0To1(2, true);

		// sync second ACTIVATE and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// assert that no session was aborted and no success event was broadcast
		assertFalse(listener1.succeeded);
		assertFalse(listener2.succeeded);
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	@Test
	public void testIntroductionToRemovedContact() throws Exception {
		// let contact1 and contact2 add each other
		addContacts1And2();
		assertNotNull(contactId2From1);
		assertNotNull(contactId1From2);

		// only introducee1 removes introducee2
		contactManager1.removeContact(contactId2From1);

		// both will accept the introduction
		addListeners(true, true);

		// make the introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// sync REQUEST messages
		sync0To1(1, true);
		sync0To2(1, true);

		// sync ACCEPT messages back to introducer
		sync1To0(1, true);
		sync2To0(1, true);

		// sync forwarded ACCEPT messages to introducees
		sync0To1(1, true);
		sync0To2(1, true);

		// sync first AUTH and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// sync second AUTH and its forward as well as the following ACTIVATE
		sync2To0(2, true);
		sync0To1(2, true);

		// sync second ACTIVATE and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// Introduction only succeeded for introducee1
		assertTrue(listener1.succeeded);
		assertFalse(listener2.succeeded);

		// assert that no session was aborted
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	/**
	 * One introducee illegally sends two ACCEPT messages in a row.
	 * The introducer should notice this and ABORT the session.
	 */
	@Test
	public void testDoubleAccept() throws Exception {
		addListeners(true, true);

		// make the introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// sync REQUEST to introducee1
		sync0To1(1, true);

		// save ACCEPT from introducee1
		AcceptMessage m = (AcceptMessage) getMessageFor(c1.getClientHelper(),
				contact0From1, ACCEPT);

		// sync ACCEPT back to introducer
		sync1To0(1, true);

		// fake a second ACCEPT message from introducee1
		Message msg = c1.getMessageEncoder()
				.encodeAcceptMessage(m.getGroupId(), m.getTimestamp() + 1,
						m.getMessageId(), m.getSessionId(),
						m.getEphemeralPublicKey(), m.getAcceptTimestamp(),
						m.getTransportProperties());
		c1.getClientHelper().addLocalMessage(msg, new BdfDictionary(), true);

		// sync fake ACCEPT back to introducer
		sync1To0(1, true);

		assertTrue(listener0.aborted);
	}

	/**
	 * One introducee sends an ACCEPT and then another DECLINE message.
	 * The introducer should notice this and ABORT the session.
	 */
	@Test
	public void testAcceptAndDecline() throws Exception {
		addListeners(true, true);

		// make the introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// sync REQUEST to introducee1
		sync0To1(1, true);

		// save ACCEPT from introducee1
		AcceptMessage m = (AcceptMessage) getMessageFor(c1.getClientHelper(),
				contact0From1, ACCEPT);

		// sync ACCEPT back to introducer
		sync1To0(1, true);

		// fake a second DECLINE message also from introducee1
		Message msg = c1.getMessageEncoder()
				.encodeDeclineMessage(m.getGroupId(), m.getTimestamp() + 1,
						m.getMessageId(), m.getSessionId());
		c1.getClientHelper().addLocalMessage(msg, new BdfDictionary(), true);

		// sync fake DECLINE back to introducer
		sync1To0(1, true);

		assertTrue(listener0.aborted);
	}

	/**
	 * One introducee sends an DECLINE and then another DECLINE message.
	 * The introducer should notice this and ABORT the session.
	 */
	@Test
	public void testDoubleDecline() throws Exception {
		addListeners(false, true);

		// make the introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// sync REQUEST to introducee1
		sync0To1(1, true);

		// save DECLINE from introducee1
		DeclineMessage m = (DeclineMessage) getMessageFor(c1.getClientHelper(),
				contact0From1, DECLINE);

		// sync DECLINE back to introducer
		sync1To0(1, true);

		// fake a second DECLINE message also from introducee1
		Message msg = c1.getMessageEncoder()
				.encodeDeclineMessage(m.getGroupId(), m.getTimestamp() + 1,
						m.getMessageId(), m.getSessionId());
		c1.getClientHelper().addLocalMessage(msg, new BdfDictionary(), true);

		// sync fake DECLINE back to introducer
		sync1To0(1, true);

		assertTrue(listener0.aborted);
	}

	/**
	 * One introducee sends two AUTH messages.
	 * The introducer should notice this and ABORT the session.
	 */
	@Test
	public void testDoubleAuth() throws Exception {
		addListeners(true, true);

		// make the introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// sync REQUEST messages
		sync0To1(1, true);
		sync0To2(1, true);

		// sync ACCEPT messages
		sync1To0(1, true);
		sync2To0(1, true);

		// sync forwarded ACCEPT messages to introducees
		sync0To1(1, true);
		sync0To2(1, true);

		// save AUTH from introducee1
		AuthMessage m = (AuthMessage) getMessageFor(c1.getClientHelper(),
				contact0From1, AUTH);

		// sync first AUTH message
		sync1To0(1, true);

		// fake a second AUTH message also from introducee1
		Message msg = c1.getMessageEncoder()
				.encodeAuthMessage(m.getGroupId(), m.getTimestamp() + 1,
						m.getMessageId(), m.getSessionId(), m.getMac(),
						m.getSignature());
		c1.getClientHelper().addLocalMessage(msg, new BdfDictionary(), true);

		// sync second AUTH message
		sync1To0(1, true);

		assertTrue(listener0.aborted);
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

		// get local group for introducee1
		Group group1 = getLocalGroup();

		// check that we have one session state
		assertEquals(1, c1.getClientHelper()
				.getMessageMetadataAsDictionary(group1.getId()).size());

		// introducee1 removes introducer
		contactManager1.removeContact(contactId0From1);

		// make sure local state got deleted
		assertEquals(0, c1.getClientHelper()
				.getMessageMetadataAsDictionary(group1.getId()).size());
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

		// get local group for introducer
		Group group0 = getLocalGroup();

		// check that we have one session state
		assertEquals(1, c0.getClientHelper()
				.getMessageMetadataAsDictionary(group0.getId()).size());

		// introducer removes introducee1
		contactManager0.removeContact(contactId1From0);

		// make sure local state is still there
		assertEquals(1, c0.getClientHelper()
				.getMessageMetadataAsDictionary(group0.getId()).size());

		// ensure introducer has aborted the session
		assertTrue(listener0.aborted);

		// sync REQUEST and ABORT message
		sync0To2(2, true);

		// ensure introducee2 has aborted the session as well
		assertTrue(listener2.aborted);

		// introducer removes other introducee
		contactManager0.removeContact(contactId2From0);

		// make sure local state is gone now
		assertEquals(0, c0.getClientHelper()
				.getMessageMetadataAsDictionary(group0.getId()).size());
	}

	@Test
	public void testIntroductionAfterReAddingContacts() throws Exception {
		// make introduction
		long time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// 0 and 1 remove and re-add each other
		contactManager0.removeContact(contactId1From0);
		contactManager1.removeContact(contactId0From1);
		contactId1From0 = contactManager0
				.addContact(author1, author0.getId(), getSecretKey(),
						clock.currentTimeMillis(), true, true, true);
		contact1From0 = contactManager0.getContact(contactId1From0);
		contactId0From1 = contactManager1
				.addContact(author0, author1.getId(), getSecretKey(),
						clock.currentTimeMillis(), true, true, true);
		contact0From1 = contactManager1.getContact(contactId0From1);

		// Sync initial client versioning updates and transport properties
		sync0To1(1, true);
		sync1To0(1, true);
		sync0To1(2, true);
		sync1To0(1, true);

		// a new introduction should be possible
		assertTrue(introductionManager0
				.canIntroduce(contact1From0, contact2From0));

		// listen to events, so we don't miss new request
		addListeners(true, true);

		// make new introduction
		time = clock.currentTimeMillis();
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null, time);

		// introduction should sync and not be INVALID or PENDING
		sync0To1(1, true);

		// assert that new request was received
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);
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
		AcceptMessage message =
				(AcceptMessage) getMessageFor(c0.getClientHelper(),
						contact2From0, ACCEPT);

		// allow visitor to modify response
		AcceptMessage m = visitor.visit(message);

		// replace original response with modified one
		Transaction txn = db0.startTransaction(false);
		try {
			db0.removeMessage(txn, message.getMessageId());
			Message msg = c0.getMessageEncoder()
					.encodeAcceptMessage(m.getGroupId(), m.getTimestamp(),
							m.getPreviousMessageId(), m.getSessionId(),
							m.getEphemeralPublicKey(), m.getAcceptTimestamp(),
							m.getTransportProperties());
			c0.getClientHelper()
					.addLocalMessage(txn, msg, new BdfDictionary(), true);
			Group group0 = getLocalGroup();
			BdfDictionary query = BdfDictionary.of(
					new BdfEntry(SESSION_KEY_SESSION_ID, m.getSessionId())
			);
			Map.Entry<MessageId, BdfDictionary> session = c0.getClientHelper()
					.getMessageMetadataAsDictionary(txn, group0.getId(), query)
					.entrySet().iterator().next();
			replacePreviousLocalMessageId(contact2From0.getAuthor(),
					session.getValue(), msg.getId());
			c0.getClientHelper().mergeMessageMetadata(txn, session.getKey(),
					session.getValue());
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

		// sync first AUTH and forward it
		sync1To0(1, true);
		sync0To2(1, true);

		// introducee2 should have detected the fake now
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertTrue(listener2.aborted);

		// sync introducee2's ack and following abort
		sync2To0(2, true);

		// ensure introducer got the abort
		assertTrue(listener0.aborted);

		// sync abort messages to introducees
		sync0To1(2, true);

		// ensure everybody got the abort now
		assertTrue(listener0.aborted);
		assertTrue(listener1.aborted);
		assertTrue(listener2.aborted);
	}

	@Test
	public void testModifiedTransportProperties() throws Exception {
		testModifiedResponse(
				m -> new AcceptMessage(m.getMessageId(), m.getGroupId(),
						m.getTimestamp(), m.getPreviousMessageId(),
						m.getSessionId(), m.getEphemeralPublicKey(),
						m.getAcceptTimestamp(),
						getTransportPropertiesMap(2))
		);
	}

	@Test
	public void testModifiedTimestamp() throws Exception {
		testModifiedResponse(
				m -> new AcceptMessage(m.getMessageId(), m.getGroupId(),
						m.getTimestamp(), m.getPreviousMessageId(),
						m.getSessionId(), m.getEphemeralPublicKey(),
						clock.currentTimeMillis(),
						m.getTransportProperties())
		);
	}

	@Test
	public void testModifiedEphemeralPublicKey() throws Exception {
		testModifiedResponse(
				m -> new AcceptMessage(m.getMessageId(), m.getGroupId(),
						m.getTimestamp(), m.getPreviousMessageId(),
						m.getSessionId(),
						getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
						m.getAcceptTimestamp(), m.getTransportProperties())
		);
	}

	private void addTransportProperties()
			throws DbException, IOException, TimeoutException {
		TransportPropertyManager tpm0 = c0.getTransportPropertyManager();
		TransportPropertyManager tpm1 = c1.getTransportPropertyManager();
		TransportPropertyManager tpm2 = c2.getTransportPropertyManager();

		tpm0.mergeLocalProperties(TRANSPORT_ID, getTransportProperties(2));
		sync0To1(1, true);
		sync0To2(1, true);

		tpm1.mergeLocalProperties(TRANSPORT_ID, getTransportProperties(2));
		sync1To0(1, true);

		tpm2.mergeLocalProperties(TRANSPORT_ID, getTransportProperties(2));
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
	private abstract class IntroductionListener implements EventListener {

		protected volatile boolean aborted = false;
		protected volatile Event latestEvent;

		@SuppressWarnings("WeakerAccess")
		IntroductionResponse getResponse() {
			assertTrue(
					latestEvent instanceof IntroductionResponseReceivedEvent);
			return ((IntroductionResponseReceivedEvent) latestEvent)
					.getIntroductionResponse();
		}
	}

	@MethodsNotNullByDefault
	@ParametersNotNullByDefault
	private class IntroduceeListener extends IntroductionListener {

		private volatile boolean requestReceived = false;
		private volatile boolean succeeded = false;
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
				latestEvent = e;
				IntroductionRequestReceivedEvent introEvent =
						((IntroductionRequestReceivedEvent) e);
				requestReceived = true;
				IntroductionRequest ir = introEvent.getIntroductionRequest();
				ContactId contactId = introEvent.getContactId();
				sessionId = ir.getSessionId();
				long time = clock.currentTimeMillis();
				try {
					if (introducee == 1 && answerRequests) {
						introductionManager1
								.respondToIntroduction(contactId, sessionId,
										time, accept);
					} else if (introducee == 2 && answerRequests) {
						introductionManager2
								.respondToIntroduction(contactId, sessionId,
										time, accept);
					}
				} catch (DbException exception) {
					eventWaiter.rethrow(exception);
				} finally {
					eventWaiter.resume();
				}
			} else if (e instanceof IntroductionResponseReceivedEvent) {
				// only broadcast for DECLINE messages in introducee role
				latestEvent = e;
				eventWaiter.resume();
			} else if (e instanceof IntroductionSucceededEvent) {
				latestEvent = e;
				succeeded = true;
				Contact contact = ((IntroductionSucceededEvent) e).getContact();
				eventWaiter
						.assertFalse(contact.getId().equals(contactId0From1));
				eventWaiter.resume();
			} else if (e instanceof IntroductionAbortedEvent) {
				latestEvent = e;
				aborted = true;
				eventWaiter.resume();
			}
		}

		private IntroductionRequest getRequest() {
			assertTrue(
					latestEvent instanceof IntroductionRequestReceivedEvent);
			return ((IntroductionRequestReceivedEvent) latestEvent)
					.getIntroductionRequest();
		}
	}

	@NotNullByDefault
	private class IntroducerListener extends IntroductionListener {

		private volatile boolean response1Received = false;
		private volatile boolean response2Received = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof IntroductionResponseReceivedEvent) {
				latestEvent = e;
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
				latestEvent = e;
				aborted = true;
				eventWaiter.resume();
			}
		}

	}

	private void replacePreviousLocalMessageId(Author author,
			BdfDictionary d, MessageId id) throws FormatException {
		BdfDictionary i1 = d.getDictionary(SESSION_KEY_INTRODUCEE_A);
		BdfDictionary i2 = d.getDictionary(SESSION_KEY_INTRODUCEE_B);
		Author a1 = clientHelper
				.parseAndValidateAuthor(i1.getList(SESSION_KEY_AUTHOR));
		Author a2 = clientHelper
				.parseAndValidateAuthor(i2.getList(SESSION_KEY_AUTHOR));

		if (a1.equals(author)) {
			i1.put(SESSION_KEY_LAST_LOCAL_MESSAGE_ID, id);
			d.put(SESSION_KEY_INTRODUCEE_A, i1);
		} else if (a2.equals(author)) {
			i2.put(SESSION_KEY_LAST_LOCAL_MESSAGE_ID, id);
			d.put(SESSION_KEY_INTRODUCEE_B, i2);
		} else {
			throw new AssertionError();
		}
	}

	private AbstractIntroductionMessage getMessageFor(ClientHelper ch,
			Contact contact, MessageType type)
			throws FormatException, DbException {
		Group g = introductionManager0.getContactGroup(contact);
		BdfDictionary query = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, type.getValue())
		);
		Map<MessageId, BdfDictionary> map =
				ch.getMessageMetadataAsDictionary(g.getId(), query);
		assertEquals(1, map.size());
		MessageId id = map.entrySet().iterator().next().getKey();
		Message m = ch.getMessage(id);
		BdfList body = ch.getMessageAsList(id);
		if (type == ACCEPT) {
			//noinspection ConstantConditions
			return c0.getMessageParser().parseAcceptMessage(m, body);
		} else if (type == DECLINE) {
			//noinspection ConstantConditions
			return c0.getMessageParser().parseDeclineMessage(m, body);
		} else if (type == AUTH) {
			//noinspection ConstantConditions
			return c0.getMessageParser().parseAuthMessage(m, body);
		} else throw new AssertionError("Not implemented");
	}

	private IntroductionRequest getIntroductionRequest(
			IntroductionManager manager, ContactId contactId)
			throws DbException {
		for (IntroductionMessage im : manager
				.getIntroductionMessages(contactId)) {
			if (im instanceof IntroductionRequest) {
				return (IntroductionRequest) im;
			}
		}
		throw new AssertionError("No IntroductionRequest found");
	}

	private IntroducerSession getIntroducerSession()
			throws DbException, FormatException {
		Map<MessageId, BdfDictionary> dicts = c0.getClientHelper()
				.getMessageMetadataAsDictionary(getLocalGroup().getId());
		assertEquals(1, dicts.size());
		BdfDictionary d = dicts.values().iterator().next();
		return c0.getSessionParser().parseIntroducerSession(d);
	}

	private IntroduceeSession getIntroduceeSession(
			IntroductionIntegrationTestComponent c)
			throws DbException, FormatException {
		Map<MessageId, BdfDictionary> dicts = c.getClientHelper()
				.getMessageMetadataAsDictionary(getLocalGroup().getId());
		assertEquals(1, dicts.size());
		BdfDictionary d = dicts.values().iterator().next();
		Group introducerGroup =
				introductionManager2.getContactGroup(contact0From2);
		return c.getSessionParser()
				.parseIntroduceeSession(introducerGroup.getId(), d);
	}

	private Group getLocalGroup() {
		return contactGroupFactory.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
	}

}
