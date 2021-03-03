package org.briarproject.briar.introduction;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
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
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.ConversationResponse;
import org.briarproject.briar.api.conversation.DeletionResult;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.introduction.event.IntroductionAbortedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionRequestReceivedEvent;
import org.briarproject.briar.api.introduction.event.IntroductionResponseReceivedEvent;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.briarproject.bramble.test.TestPluginConfigModule.SIMPLEX_TRANSPORT_ID;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTransportProperties;
import static org.briarproject.bramble.test.TestUtils.getTransportPropertiesMap;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
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

	private Group g1, g2;

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

		g1 = introductionManager0.getContactGroup(contact1From0);
		g2 = introductionManager0.getContactGroup(contact2From0);

		// initialize waiter fresh for each test
		eventWaiter = new Waiter();

		addTransportProperties();
	}

	@Override
	protected void createComponents() {
		IntroductionIntegrationTestComponent component =
				DaggerIntroductionIntegrationTestComponent.builder().build();
		IntroductionIntegrationTestComponent.Helper
				.injectEagerSingletons(component);
		component.inject(this);

		c0 = DaggerIntroductionIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t0Dir))
				.build();
		IntroductionIntegrationTestComponent.Helper.injectEagerSingletons(c0);

		c1 = DaggerIntroductionIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t1Dir))
				.build();
		IntroductionIntegrationTestComponent.Helper.injectEagerSingletons(c1);

		c2 = DaggerIntroductionIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t2Dir))
				.build();
		IntroductionIntegrationTestComponent.Helper.injectEagerSingletons(c2);
	}

	@Test
	public void testIntroductionSession() throws Exception {
		addListeners(true, true);

		// make introduction
		Contact introducee1 = contact1From0;
		Contact introducee2 = contact2From0;
		introductionManager0.makeIntroduction(introducee1, introducee2, "Hi!");

		// check that messages are tracked properly
		Group g1 = introductionManager0.getContactGroup(introducee1);
		Group g2 = introductionManager0.getContactGroup(introducee2);
		assertGroupCount(messageTracker0, g1.getId(), 1, 0);
		assertGroupCount(messageTracker0, g2.getId(), 1, 0);

		// check that request message states are correct
		Collection<ConversationMessageHeader> messages = getMessages1From0();
		assertEquals(1, messages.size());
		assertMessageState(messages.iterator().next(), true, false, false);
		messages = getMessages2From0();
		assertEquals(1, messages.size());
		assertMessageState(messages.iterator().next(), true, false, false);

		// sync first REQUEST message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);
		assertEquals(introducee2.getAuthor().getName(),
				listener1.getRequest().getName());
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);

		// check that accept message state is correct
		messages = getMessages0From1();
		assertEquals(2, messages.size());
		for (ConversationMessageHeader h : messages) {
			if (h instanceof ConversationResponse) {
				assertMessageState(h, true, false, false);
			}
		}

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
				listener0.getResponse().getIntroducedAuthor().getName());
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);
		assertTrue(listener0.getResponse().canSucceed());

		// sync second ACCEPT message
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener0.response2Received);
		assertEquals(introducee1.getAuthor().getName(),
				listener0.getResponse().getIntroducedAuthor().getName());
		assertGroupCount(messageTracker0, g2.getId(), 2, 1);
		assertTrue(listener0.getResponse().canSucceed());

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
		for (Contact c : contactManager1.getContacts()) {
			if (c.getAuthor().equals(author2)) {
				assertFalse(c.isVerified());
			}
		}
		for (Contact c : contactManager2.getContacts()) {
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
	public void testIntroductionSessionWithAutoDelete() throws Exception {
		addListeners(true, true);

		// 0 and 1 set an auto-delete timer for their conversation
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		setAutoDeleteTimer(c1, contactId0From1, MIN_AUTO_DELETE_TIMER_MS);

		// Make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

		// Sync first REQUEST message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// Sync second REQUEST message
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// Sync first ACCEPT message
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// Sync second ACCEPT message
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// Sync forwarded ACCEPT messages to introducees
		sync0To1(1, true);
		sync0To2(1, true);

		// Sync first AUTH and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// Sync second AUTH and its forward as well as the following ACTIVATE
		sync2To0(2, true);
		sync0To1(2, true);

		// Sync second ACTIVATE and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// Wait for introduction to succeed
		eventWaiter.await(TIMEOUT, 2);
		assertTrue(listener1.succeeded);
		assertTrue(listener2.succeeded);

		// All visible messages between 0 and 1 should have auto-delete timers
		for (ConversationMessageHeader h : getMessages1From0()) {
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		}
		for (ConversationMessageHeader h : getMessages0From1()) {
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		}
		// No visible messages between 0 and 2 should have auto-delete timers
		for (ConversationMessageHeader h : getMessages2From0()) {
			assertEquals(NO_AUTO_DELETE_TIMER, h.getAutoDeleteTimer());
		}
		for (ConversationMessageHeader h : getMessages0From2()) {
			assertEquals(NO_AUTO_DELETE_TIMER, h.getAutoDeleteTimer());
		}
	}

	@Test
	public void testIntroductionSessionFirstDecline() throws Exception {
		addListeners(false, true);

		// make introduction
		Contact introducee1 = contact1From0;
		Contact introducee2 = contact2From0;
		introductionManager0.makeIntroduction(introducee1, introducee2, null);

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
				listener0.getResponse().getIntroducedAuthor().getName());
		assertFalse(listener0.getResponse().canSucceed());

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
				listener2.getResponse().getIntroducedAuthor().getName());
		assertFalse(listener2.getResponse().canSucceed());

		assertFalse(listener1.succeeded);
		assertFalse(listener2.succeeded);

		assertFalse(contactManager1
				.contactExists(author2.getId(), author1.getId()));
		assertFalse(contactManager2
				.contactExists(author1.getId(), author2.getId()));

		Group g1 = introductionManager0.getContactGroup(introducee1);
		Group g2 = introductionManager0.getContactGroup(introducee2);
		Collection<ConversationMessageHeader> messages = getMessages1From0();
		assertEquals(2, messages.size());
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);
		messages = getMessages2From0();
		assertEquals(2, messages.size());
		assertGroupCount(messageTracker0, g2.getId(), 2, 1);
		messages = getMessages0From1();
		assertEquals(2, messages.size());
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);
		// introducee2 should also have the decline response of introducee1
		messages = getMessages0From2();
		assertEquals(3, messages.size());
		assertGroupCount(messageTracker2, g2.getId(), 3, 2);

		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	@Test
	public void testIntroductionSessionSecondDecline() throws Exception {
		addListeners(true, false);

		// make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

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
				listener1.getResponse().getIntroducedAuthor().getName());
		assertFalse(listener1.getResponse().canSucceed());

		assertFalse(contactManager1
				.contactExists(author2.getId(), author1.getId()));
		assertFalse(contactManager2
				.contactExists(author1.getId(), author2.getId()));

		Collection<ConversationMessageHeader> messages = getMessages1From0();
		assertEquals(2, messages.size());
		messages = getMessages2From0();
		assertEquals(2, messages.size());
		messages = getMessages0From1();
		assertEquals(3, messages.size());
		messages = getMessages0From2();
		assertEquals(2, messages.size());
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	@Test
	public void testNewIntroductionAfterDecline() throws Exception {
		addListeners(false, true);

		// make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

		// sync request messages
		sync0To1(1, true);
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 2);

		// sync first response
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// sync second response
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// sync both forwarded response
		sync0To2(1, true);
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);

		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

		// sync request messages
		sync0To1(1, true);
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 2);

		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	@Test
	public void testResponseAndAuthInOneSync() throws Exception {
		addListeners(true, true);

		// make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

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
				listener2.sessionId, true);

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
		Contact introducee1 = contact1From0;
		Contact introducee2 = contact2From0;
		introductionManager0
				.makeIntroduction(introducee1, introducee2, null);

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
				listener2.getResponse().getIntroducedAuthor().getName());

		// assert that introducee2 is in correct state
		introduceeSession = getIntroduceeSession(c2);
		assertEquals(IntroduceeState.REMOTE_DECLINED,
				introduceeSession.getState());

		// answer request manually
		introductionManager2.respondToIntroduction(contactId0From2,
				listener2.sessionId, false);

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
		assertEquals(2, getMessages1From0().size());
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);
		assertEquals(2, getMessages2From0().size());
		assertGroupCount(messageTracker0, g2.getId(), 2, 1);
		assertEquals(2, getMessages0From1().size());
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);
		assertEquals(3, getMessages0From2().size());
		assertGroupCount(messageTracker2, g2.getId(), 3, 2);

		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	@Test
	public void testIntroductionToSameContact() throws Exception {
		addListeners(true, false);

		// make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact1From0, null);

		// sync request messages
		sync0To1(1, false);

		// we should not get any event, because the request will be discarded
		assertFalse(listener1.requestReceived);

		// make really sure we don't have that request
		assertTrue(getMessages0From1().isEmpty());

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
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

		// no more introduction allowed while the existing one is in progress
		assertFalse(introductionManager0
				.canIntroduce(contact1From0, contact2From0));

		// try it anyway and fail
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);
	}

	@Test
	public void testIntroductionToExistingContact() throws Exception {
		// let contact1 and contact2 add each other already
		addContacts1And2(true);
		assertNotNull(contactId2From1);
		assertNotNull(contactId1From2);

		// both will still accept the introduction
		addListeners(true, true);

		// make the introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

		// sync REQUEST messages
		sync0To1(1, true);
		sync0To2(1, true);

		// assert that introducees get notified about the existing contact
		IntroductionRequest ir1 = getIntroductionRequest(db1,
				introductionManager1, contactId0From1);
		assertTrue(ir1.isContact());
		IntroductionRequest ir2 = getIntroductionRequest(db2,
				introductionManager2, contactId0From2);
		assertTrue(ir2.isContact());

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
		addContacts1And2(true);
		assertNotNull(contactId2From1);
		assertNotNull(contactId1From2);

		// only introducee1 removes introducee2
		contactManager1.removeContact(contactId2From1);

		// both will accept the introduction
		addListeners(true, true);

		// make the introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

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
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

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
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

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
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

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
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

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
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

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
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

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
		eventWaiter.await(TIMEOUT, 1);  // wait for AbortEvent
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
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

		// 0 and 1 remove and re-add each other
		contactManager0.removeContact(contactId1From0);
		contactManager1.removeContact(contactId0From1);
		SecretKey rootKey0_1 = getSecretKey();
		contactId1From0 = contactManager0.addContact(author1, author0.getId(),
				rootKey0_1, c0.getClock().currentTimeMillis(), true, true,
				true);
		contact1From0 = contactManager0.getContact(contactId1From0);
		contactId0From1 = contactManager1.addContact(author0, author1.getId(),
				rootKey0_1, c1.getClock().currentTimeMillis(), false, true,
				true);
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
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, null);

		// introduction should sync and not be INVALID or PENDING
		sync0To1(1, true);

		// assert that new request was received
		eventWaiter.await(TIMEOUT, 1);
		assertTrue(listener1.requestReceived);
	}

	private void testModifiedResponse(StateVisitor visitor) throws Exception {
		addListeners(true, true);

		// make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

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
		db0.transaction(false, txn -> {
			db0.removeMessage(txn, message.getMessageId());
			Message msg = c0.getMessageEncoder()
					.encodeAcceptMessage(m.getGroupId(), m.getTimestamp(),
							m.getPreviousMessageId(), m.getSessionId(),
							m.getEphemeralPublicKey(), m.getAcceptTimestamp(),
							m.getTransportProperties());
			c0.getClientHelper().addLocalMessage(txn, msg, new BdfDictionary(),
					true, false);
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
		});

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
						getTransportPropertiesMap(2), NO_AUTO_DELETE_TIMER)
		);
	}

	@Test
	public void testModifiedTimestamp() throws Exception {
		testModifiedResponse(
				m -> new AcceptMessage(m.getMessageId(), m.getGroupId(),
						m.getTimestamp(), m.getPreviousMessageId(),
						m.getSessionId(), m.getEphemeralPublicKey(),
						c0.getClock().currentTimeMillis(),
						m.getTransportProperties(), NO_AUTO_DELETE_TIMER)
		);
	}

	@Test
	public void testModifiedEphemeralPublicKey() throws Exception {
		testModifiedResponse(
				m -> new AcceptMessage(m.getMessageId(), m.getGroupId(),
						m.getTimestamp(), m.getPreviousMessageId(),
						m.getSessionId(), getAgreementPublicKey(),
						m.getAcceptTimestamp(), m.getTransportProperties(),
						NO_AUTO_DELETE_TIMER)
		);
	}

	@Test
	public void testDeletingAllMessagesWhenCompletingSession()
			throws Exception {
		addListeners(true, true);

		// make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

		// sync first REQUEST message
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// introducer can not yet remove messages
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertTrue(
				deleteAllMessages1From0().hasIntroductionSessionInProgress());
		// introducee1 can not yet remove messages
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(
				deleteAllMessages0From1().hasIntroductionSessionInProgress());

		// sync second REQUEST message
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// introducer can not yet remove messages
		assertFalse(deleteAllMessages2From0().allDeleted());
		assertTrue(
				deleteAllMessages2From0().hasIntroductionSessionInProgress());
		// introducee2 can not yet remove messages
		assertFalse(deleteAllMessages0From2().allDeleted());
		assertTrue(
				deleteAllMessages0From2().hasIntroductionSessionInProgress());

		// sync first ACCEPT message
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// introducer can not yet remove messages
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertTrue(
				deleteAllMessages1From0().hasIntroductionSessionInProgress());

		// sync second ACCEPT message
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// introducer can not yet remove messages
		assertFalse(deleteAllMessages2From0().allDeleted());
		assertTrue(
				deleteAllMessages2From0().hasIntroductionSessionInProgress());

		// sync forwarded ACCEPT messages to introducees
		sync0To1(1, true);
		sync0To2(1, true);

		// introducee1 can not yet remove messages
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(
				deleteAllMessages0From1().hasIntroductionSessionInProgress());
		// introducee2 can not yet remove messages
		assertFalse(deleteAllMessages0From2().allDeleted());
		assertTrue(
				deleteAllMessages0From2().hasIntroductionSessionInProgress());

		// sync first AUTH and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// introducer can not yet remove messages
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertTrue(
				deleteAllMessages1From0().hasIntroductionSessionInProgress());
		assertFalse(deleteAllMessages2From0().allDeleted());
		assertTrue(
				deleteAllMessages2From0().hasIntroductionSessionInProgress());
		// introducee2 can not yet remove messages
		assertFalse(deleteAllMessages0From2().allDeleted());
		assertTrue(
				deleteAllMessages0From2().hasIntroductionSessionInProgress());

		// sync second AUTH and its forward as well as the following ACTIVATE
		sync2To0(2, true);
		sync0To1(2, true);

		// introducer can not yet remove messages
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertTrue(
				deleteAllMessages1From0().hasIntroductionSessionInProgress());
		assertFalse(deleteAllMessages2From0().allDeleted());
		assertTrue(
				deleteAllMessages2From0().hasIntroductionSessionInProgress());
		// introducee1 can not yet remove messages
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertTrue(
				deleteAllMessages0From1().hasIntroductionSessionInProgress());

		// sync second ACTIVATE and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// wait for introduction to succeed
		eventWaiter.await(TIMEOUT, 2);
		assertTrue(listener1.succeeded);
		assertTrue(listener2.succeeded);

		// check that introducer messages are tracked properly
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);

		// introducer can now remove messages
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertEquals(0, getMessages1From0().size());
		// a second time returns true
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertGroupCount(messageTracker0, g1.getId(), 0, 0);

		// introducee1 can not yet remove messages, because last not ACKed
		DeletionResult result = deleteAllMessages0From1();
		assertFalse(result.allDeleted());
		assertTrue(result.hasIntroductionSessionInProgress());
		assertEquals(2, getMessages0From1().size());

		// check that introducee1 messages are tracked properly
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);

		// ACK last message
		ack0To1(1);

		// introducee1 can now remove messages
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertEquals(0, getMessages0From1().size());
		// a second time returns true
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertGroupCount(messageTracker1, g1.getId(), 0, 0);

		// check that introducee2 messages are tracked properly
		assertGroupCount(messageTracker2, g2.getId(), 2, 1);

		// introducee2 can remove messages (last message was incoming)
		assertTrue(deleteAllMessages0From2().allDeleted());
		assertEquals(0, getMessages0From2().size());
		// a second time returns true
		assertTrue(deleteAllMessages0From2().allDeleted());
		assertGroupCount(messageTracker2, g2.getId(), 0, 0);

		// a new introduction is still possible
		assertTrue(introductionManager0
				.canIntroduce(contact1From0, contact2From0));
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Ho!");
		sync0To1(1, true);
		sync0To2(1, true);

		// sync responses
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// no one should have aborted until now
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);

		// nobody can delete anything again
		result = deleteAllMessages1From0();
		assertFalse(result.allDeleted());
		assertTrue(result.hasIntroductionSessionInProgress());
		result = deleteAllMessages2From0();
		assertFalse(result.allDeleted());
		assertTrue(result.hasIntroductionSessionInProgress());
		result = deleteAllMessages0From1();
		assertFalse(result.allDeleted());
		assertTrue(result.hasIntroductionSessionInProgress());
		result = deleteAllMessages0From2();
		assertFalse(result.allDeleted());
		assertTrue(result.hasIntroductionSessionInProgress());

		// group counts get counted up again correctly
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);
		assertGroupCount(messageTracker2, g2.getId(), 2, 1);
	}

	@Test
	public void testDeletingAllMessagesWhenDeclining() throws Exception {
		addListeners(false, false);

		// make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

		// sync REQUEST messages
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// sync first DECLINE message
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// introducer can not yet remove messages
		assertFalse(deleteAllMessages1From0().allDeleted());
		// introducee1 can not yet remove messages
		assertFalse(deleteAllMessages0From1().allDeleted());

		// sync second DECLINE message
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// introducer can not yet remove messages
		assertFalse(deleteAllMessages2From0().allDeleted());
		// introducee2 can not yet remove messages
		assertFalse(deleteAllMessages0From2().allDeleted());

		// forward first DECLINE message
		sync0To2(1, true);

		// introducee2 can now remove messages
		assertTrue(deleteAllMessages0From2().allDeleted());
		assertEquals(0, getMessages0From2().size());
		// a second time nothing happens
		assertTrue(deleteAllMessages0From2().allDeleted());

		// forward second DECLINE message
		sync0To1(1, true);

		// introducee1 can now remove messages
		assertTrue(deleteAllMessages0From1().allDeleted());
		assertEquals(0, getMessages0From1().size());
		// a second time nothing happens
		assertTrue(deleteAllMessages0From1().allDeleted());

		// introducer can not yet remove messages
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertFalse(deleteAllMessages2From0().allDeleted());

		// introducer can remove messages after getting ACK from introducee1
		ack1To0(1);
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertEquals(0, getMessages1From0().size());
		// a second time nothing happens
		assertTrue(deleteAllMessages1From0().allDeleted());

		// introducer can remove messages after getting ACK from introducee2
		ack2To0(1);
		assertTrue(deleteAllMessages2From0().allDeleted());
		assertEquals(0, getMessages2From0().size());
		// a second time nothing happens
		assertTrue(deleteAllMessages2From0().allDeleted());

		// a new introduction is still possible
		assertTrue(introductionManager0
				.canIntroduce(contact1From0, contact2From0));
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Ho!");
		sync0To1(1, true);
		sync0To2(1, true);

		// sync responses
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// no one should have aborted until now
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);

		// nobody can delete anything again
		assertFalse(deleteAllMessages1From0().allDeleted());
		assertFalse(deleteAllMessages2From0().allDeleted());
		assertFalse(deleteAllMessages0From1().allDeleted());
		assertFalse(deleteAllMessages0From2().allDeleted());
	}

	@Test
	public void testDeletingOneSideOfSession() throws Exception {
		addListeners(false, false);

		// make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

		// sync REQUEST messages
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// sync DECLINE messages
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// forward DECLINE messages
		sync0To2(1, true);
		sync0To1(1, true);

		// introducer can remove messages after getting ACK from introducee1
		ack1To0(1);
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertEquals(0, getMessages1From0().size());
		// a second time nothing happens
		assertTrue(deleteAllMessages1From0().allDeleted());

		// a new introduction is still possible
		assertTrue(introductionManager0
				.canIntroduce(contact1From0, contact2From0));
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Ho!");
		sync0To1(1, true);
		sync0To2(1, true);

		// sync and forward DECLINE messages
		sync1To0(1, true);
		sync2To0(1, true);
		sync0To2(1, true);
		sync0To1(1, true);

		// introducer can remove messages after getting ACK from introducee1
		ack1To0(1);
		assertTrue(deleteAllMessages1From0().allDeleted());
		assertEquals(0, getMessages1From0().size());
		assertTrue(deleteAllMessages1From0()
				.allDeleted());  // a second time nothing happens

		// introducer can remove messages after getting ACK from introducee2
		// if this succeeds, we still had the session object after delete above
		ack2To0(1);
		assertTrue(deleteAllMessages2From0().allDeleted());
		assertEquals(0, getMessages2From0().size());
		assertTrue(deleteAllMessages2From0()
				.allDeleted());  // a second time nothing happens

		// no one should have aborted
		assertFalse(listener0.aborted);
		assertFalse(listener1.aborted);
		assertFalse(listener2.aborted);
	}

	@Test
	public void testDeletingSomeMessages() throws Exception {
		addListeners(false, false);

		// make introduction
		introductionManager0
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

		// deleting the introduction for introducee1 will fail
		Collection<ConversationMessageHeader> m1From0 = getMessages1From0();
		assertEquals(1, m1From0.size());
		MessageId messageId1 = m1From0.iterator().next().getId();
		Set<MessageId> toDelete1 = new HashSet<>();
		toDelete1.add(messageId1);
		assertFalse(deleteMessages1From0(toDelete1).allDeleted());
		assertTrue(deleteMessages1From0(toDelete1)
				.hasIntroductionSessionInProgress());

		// deleting the introduction for introducee2 will fail as well
		Collection<ConversationMessageHeader> m2From0 = getMessages2From0();
		assertEquals(1, m2From0.size());
		MessageId messageId2 = m2From0.iterator().next().getId();
		Set<MessageId> toDelete2 = new HashSet<>();
		toDelete2.add(messageId2);
		assertFalse(deleteMessages2From0(toDelete2).allDeleted());
		assertTrue(deleteMessages2From0(toDelete2)
				.hasIntroductionSessionInProgress());

		// sync REQUEST messages
		sync0To1(1, true);
		eventWaiter.await(TIMEOUT, 1);
		sync0To2(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// deleting introduction fails, because responses did not arrive
		assertFalse(deleteMessages0From1(toDelete1).allDeleted());
		assertTrue(deleteMessages0From1(toDelete1)
				.hasIntroductionSessionInProgress());
		assertFalse(deleteMessages0From2(toDelete2).allDeleted());
		assertTrue(deleteMessages0From2(toDelete2)
				.hasIntroductionSessionInProgress());

		// remember response of introducee1 for future deletion
		Collection<ConversationMessageHeader> m0From1 = getMessages0From1();
		assertEquals(2, m0From1.size());
		MessageId response1 = null;
		for (ConversationMessageHeader h : m0From1) {
			if (!h.getId().equals(messageId1)) response1 = h.getId();
		}
		assertNotNull(response1);

		// remember response of introducee2 for future deletion
		Collection<ConversationMessageHeader> m0From2 = getMessages0From2();
		assertEquals(2, m0From2.size());
		MessageId response2 = null;
		for (ConversationMessageHeader h : m0From2) {
			if (!h.getId().equals(messageId2)) response2 = h.getId();
		}
		assertNotNull(response2);

		// sync first DECLINE message
		sync1To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// introducer can not yet remove messages
		assertFalse(deleteMessages1From0(toDelete1).allDeleted());
		// introducee1 can not yet remove messages
		assertFalse(deleteMessages0From1(toDelete1).allDeleted());

		// sync second DECLINE message
		sync2To0(1, true);
		eventWaiter.await(TIMEOUT, 1);

		// check group counts
		assertGroupCount(messageTracker0, g1.getId(), 2, 1);
		assertGroupCount(messageTracker0, g2.getId(), 2, 1);
		assertGroupCount(messageTracker1, g1.getId(), 2, 1);
		assertGroupCount(messageTracker2, g2.getId(), 2, 1);

		// introducer can now remove messages with both introducees,
		// if the responses are also selected
		Set<MessageId> toDelete1From0 = new HashSet<>(toDelete1);
		toDelete1From0.add(response1);
		DeletionResult result = deleteMessages1From0(toDelete1From0);
		assertTrue(result.allDeleted());
		Set<MessageId> toDelete2From0 = new HashSet<>(toDelete2);
		toDelete2From0.add(response2);
		assertTrue(deleteMessages2From0(toDelete2From0).allDeleted());
		assertGroupCount(messageTracker0, g1.getId(), 0, 0);
		assertGroupCount(messageTracker0, g2.getId(), 0, 0);

		// introducee2 can not yet remove messages, missing the other response
		assertFalse(deleteMessages0From1(toDelete1).allDeleted());

		// forward first DECLINE message
		sync0To2(1, true);

		// deleting introduction fails for introducee 2,
		// because response is not yet selected for deletion
		assertFalse(deleteMessages0From2(toDelete2).allDeleted());
		assertTrue(deleteMessages0From2(toDelete2)
				.hasNotAllIntroductionSelected());

		// add response to be deleted as well
		toDelete2.add(response2);

		// introducee2 can now remove messages
		assertTrue(deleteMessages0From2(toDelete2).allDeleted());
		assertEquals(0, getMessages0From2().size());
		// a second time nothing happens
		assertTrue(deleteMessages0From2(toDelete2).allDeleted());
		assertGroupCount(messageTracker2, g2.getId(), 0, 0);

		// forward second DECLINE message
		sync0To1(1, true);

		// deleting introduction fails for introducee 1,
		// because response is not yet selected for deletion
		assertFalse(deleteMessages0From1(toDelete1).allDeleted());
		assertTrue(deleteMessages0From1(toDelete1)
				.hasNotAllIntroductionSelected());

		// add response to be deleted as well
		toDelete1.add(response1);

		// introducee1 can now also remove messages
		assertTrue(deleteMessages0From1(toDelete1).allDeleted());
		assertEquals(0, getMessages0From1().size());
		// a second time nothing happens
		assertTrue(deleteMessages0From1(toDelete1).allDeleted());
		assertGroupCount(messageTracker1, g1.getId(), 0, 0);
	}

	@Test
	public void testDeletingEmptySet() throws Exception {
		assertTrue(deleteMessages0From1(emptySet()).allDeleted());
	}

	private DeletionResult deleteAllMessages1From0() throws DbException {
		return db0.transactionWithResult(false, txn -> introductionManager0
				.deleteAllMessages(txn, contactId1From0));
	}

	private DeletionResult deleteAllMessages2From0() throws DbException {
		return db0.transactionWithResult(false, txn -> introductionManager0
				.deleteAllMessages(txn, contactId2From0));
	}

	private DeletionResult deleteAllMessages0From1() throws DbException {
		return db1.transactionWithResult(false, txn -> introductionManager1
				.deleteAllMessages(txn, contactId0From1));
	}

	private DeletionResult deleteAllMessages0From2() throws DbException {
		return db2.transactionWithResult(false, txn -> introductionManager2
				.deleteAllMessages(txn, contactId0From2));
	}

	private DeletionResult deleteMessages1From0(Set<MessageId> toDelete)
			throws DbException {
		return db0.transactionWithResult(false, txn -> introductionManager0
				.deleteMessages(txn, contactId1From0, toDelete));
	}

	private DeletionResult deleteMessages2From0(Set<MessageId> toDelete)
			throws DbException {
		return db0.transactionWithResult(false, txn -> introductionManager0
				.deleteMessages(txn, contactId2From0, toDelete));
	}

	private DeletionResult deleteMessages0From1(Set<MessageId> toDelete)
			throws DbException {
		return db1.transactionWithResult(false, txn -> introductionManager1
				.deleteMessages(txn, contactId0From1, toDelete));
	}

	private DeletionResult deleteMessages0From2(Set<MessageId> toDelete)
			throws DbException {
		return db2.transactionWithResult(false, txn -> introductionManager2
				.deleteMessages(txn, contactId0From2, toDelete));
	}

	private void addTransportProperties() throws Exception {
		TransportPropertyManager tpm0 = c0.getTransportPropertyManager();
		TransportPropertyManager tpm1 = c1.getTransportPropertyManager();
		TransportPropertyManager tpm2 = c2.getTransportPropertyManager();

		tpm0.mergeLocalProperties(SIMPLEX_TRANSPORT_ID,
				getTransportProperties(2));
		sync0To1(1, true);
		sync0To2(1, true);

		tpm1.mergeLocalProperties(SIMPLEX_TRANSPORT_ID,
				getTransportProperties(2));
		sync1To0(1, true);

		tpm2.mergeLocalProperties(SIMPLEX_TRANSPORT_ID,
				getTransportProperties(2));
		sync2To0(1, true);
	}

	private void assertDefaultUiMessages() throws DbException {
		Collection<ConversationMessageHeader> messages = getMessages1From0();
		assertEquals(2, messages.size());
		assertMessagesAreAcked(messages);

		messages = getMessages2From0();
		assertEquals(2, messages.size());
		assertMessagesAreAcked(messages);

		messages = getMessages0From1();
		assertEquals(2, messages.size());
		assertMessagesAreAcked(messages);

		messages = getMessages0From2();
		assertEquals(2, messages.size());
		assertMessagesAreAcked(messages);
	}

	private Collection<ConversationMessageHeader> getMessages1From0()
			throws DbException {
		return db0.transactionWithResult(true, txn -> introductionManager0
				.getMessageHeaders(txn, contactId1From0));
	}

	private Collection<ConversationMessageHeader> getMessages2From0()
			throws DbException {
		return db0.transactionWithResult(true, txn -> introductionManager0
				.getMessageHeaders(txn, contactId2From0));
	}

	private Collection<ConversationMessageHeader> getMessages0From1()
			throws DbException {
		return db1.transactionWithResult(true, txn -> introductionManager1
				.getMessageHeaders(txn, contactId0From1));
	}

	private Collection<ConversationMessageHeader> getMessages0From2()
			throws DbException {
		return db2.transactionWithResult(true, txn -> introductionManager2
				.getMessageHeaders(txn, contactId0From2));
	}

	private void assertMessagesAreAcked(
			Collection<ConversationMessageHeader> messages) {
		for (ConversationMessageHeader msg : messages) {
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
	private abstract static class IntroductionListener
			implements EventListener {

		volatile boolean aborted = false;
		volatile Event latestEvent;

		IntroductionResponse getResponse() {
			assertTrue(
					latestEvent instanceof IntroductionResponseReceivedEvent);
			return ((IntroductionResponseReceivedEvent) latestEvent)
					.getMessageHeader();
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
				IntroductionRequest ir = introEvent.getMessageHeader();
				ContactId contactId = introEvent.getContactId();
				sessionId = ir.getSessionId();
				try {
					if (introducee == 1 && answerRequests) {
						introductionManager1.respondToIntroduction(contactId,
								sessionId, accept);
					} else if (introducee == 2 && answerRequests) {
						introductionManager2.respondToIntroduction(contactId,
								sessionId, accept);
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
			} else if (e instanceof ContactAddedEvent) {
				latestEvent = e;
				succeeded = true;
				ContactId contactId = ((ContactAddedEvent) e).getContactId();
				eventWaiter.assertFalse(contactId.equals(contactId0From1));
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
					.getMessageHeader();
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
			return c0.getMessageParser().parseAcceptMessage(m, body);
		} else if (type == DECLINE) {
			return c0.getMessageParser().parseDeclineMessage(m, body);
		} else if (type == AUTH) {
			return c0.getMessageParser().parseAuthMessage(m, body);
		} else throw new AssertionError("Not implemented");
	}

	private IntroductionRequest getIntroductionRequest(DatabaseComponent db,
			IntroductionManager manager, ContactId contactId)
			throws DbException {
		Collection<ConversationMessageHeader> messages =
				db.transactionWithResult(true, txn ->
						manager.getMessageHeaders(txn, contactId));
		for (ConversationMessageHeader im : messages) {
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
