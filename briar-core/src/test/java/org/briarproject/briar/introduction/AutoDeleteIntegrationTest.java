package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.system.TimeTravelModule;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.conversation.ConversationManager.ConversationClient;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.introduction.event.IntroductionResponseReceivedEvent;
import org.briarproject.briar.autodelete.AbstractAutoDeleteTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.briarproject.bramble.api.cleanup.CleanupManager.BATCH_DELAY_MS;
import static org.briarproject.bramble.test.TestPluginConfigModule.SIMPLEX_TRANSPORT_ID;
import static org.briarproject.bramble.test.TestUtils.getTransportProperties;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.api.introduction.IntroductionManager.CLIENT_ID;
import static org.briarproject.briar.api.introduction.IntroductionManager.MAJOR_VERSION;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSES;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSE_A;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSE_B;
import static org.briarproject.briar.introduction.IntroducerState.A_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.B_DECLINED;
import static org.briarproject.briar.introduction.IntroducerState.START;
import static org.briarproject.briar.test.TestEventListener.assertEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AutoDeleteIntegrationTest extends AbstractAutoDeleteTest {

	@Override
	protected void createComponents() {
		IntroductionIntegrationTestComponent component =
				DaggerIntroductionIntegrationTestComponent.builder().build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(component);
		component.inject(this);

		IntroductionIntegrationTestComponent c0 =
				DaggerIntroductionIntegrationTestComponent.builder()
						.testDatabaseConfigModule(
								new TestDatabaseConfigModule(t0Dir))
						.timeTravelModule(new TimeTravelModule(true))
						.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c0);

		IntroductionIntegrationTestComponent c1 =
				DaggerIntroductionIntegrationTestComponent.builder()
						.testDatabaseConfigModule(
								new TestDatabaseConfigModule(t1Dir))
						.timeTravelModule(new TimeTravelModule(true))
						.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c1);

		IntroductionIntegrationTestComponent c2 =
				DaggerIntroductionIntegrationTestComponent.builder()
						.testDatabaseConfigModule(
								new TestDatabaseConfigModule(t2Dir))
						.timeTravelModule(new TimeTravelModule(true))
						.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c2);

		this.c0 = c0;
		this.c1 = c1;
		this.c2 = c2;

		// Use different times to avoid creating identical messages that are
		// treated as redundant copies of the same message (#1907)
		try {
			c0.getTimeTravel().setCurrentTimeMillis(startTime);
			c1.getTimeTravel().setCurrentTimeMillis(startTime + 1);
			c2.getTimeTravel().setCurrentTimeMillis(startTime + 2);
		} catch (InterruptedException e) {
			fail();
		}
	}

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		addTransportProperties();
	}

	@Override
	protected ConversationClient getConversationClient(
			BriarIntegrationTestComponent component) {
		return component.getIntroductionManager();
	}

	/*
	 * Basic tests.
	 * ASSERT timers are set on introduction messages
	 * ASSERT introduction messages self-destruct on all three sides
	 */

	@Test
	public void testIntroductionMessagesHaveTimer() throws Exception {
		makeIntroduction(true, true);
		assertIntroductionsArrived();

		assertMessagesAmong0And1HaveTimerSet(1, 1);
		assertMessagesAmong0And2HaveTimerSet(1, 1);
	}

	@Test
	public void testIntroductionAutoDeleteIntroducer() throws Exception {
		long timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;

		makeIntroduction(true, true);
		assertIntroductionsArrived();

		assertMessagesAmong0And1HaveTimerSet(1, 1);
		assertMessagesAmong0And2HaveTimerSet(1, 1);

		// Sync the ack to 0 from 1 - this starts 0's timer
		ack1To0(1);
		waitForEvents(c0);

		// Before 0's timer elapses, the introducer should still see the
		// introduction sent to 1
		timeTravel(c0, timerLatency - 1);
		assertGroupCountAt0With1(1, 0);
		assertGroupCountAt0With2(1, 0);
		assertGroupCountAt1With0(1, 1);
		assertGroupCountAt2With0(1, 1);

		// After 0's timer elapses, the introducer should have deleted the
		// introduction sent to 1
		timeTravel(c0, 1);
		assertGroupCountAt0With1(0, 0);
		assertGroupCountAt0With2(1, 0);
		assertGroupCountAt1With0(1, 1);
		assertGroupCountAt2With0(1, 1);

		// Sync the ack to 0 from 2 - this starts 0's timer
		ack2To0(1);
		waitForEvents(c0);

		// Before 0's timer elapses, the introducer should still see the
		// introduction sent to 2
		timeTravel(c0, timerLatency - 1);
		assertGroupCountAt0With1(0, 0);
		assertGroupCountAt0With2(1, 0);
		assertGroupCountAt1With0(1, 1);
		assertGroupCountAt2With0(1, 1);

		// After 0's timer elapses, the introducer should have deleted the
		// introduction sent to 2
		timeTravel(c0, 1);
		assertGroupCountAt0With1(0, 0);
		assertGroupCountAt0With2(0, 0);
		assertGroupCountAt1With0(1, 1);
		assertGroupCountAt2With0(1, 1);
	}

	@Test
	public void testIntroductionAutoDeleteIntroducee() throws Exception {
		long timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;

		makeIntroduction(true, false);

		markMessagesRead(c1, contact0From1);
		assertGroupCountAt1With0(1, 0);
		assertGroupCountAt2With0(1, 1);

		// Travel in time at c1 unit 1ms before the deadline expires
		timeTravel(c1, timerLatency - 1);
		assertGroupCountAt0With1(1, 0);
		assertGroupCountAt0With2(1, 0);
		assertGroupCountAt2With0(1, 1);
		// There is currently 1 message at 1 with 0, the introduction request
		assertGroupCountAt1With0(1, 0);
		forEachHeader(c1, contactId0From1, 1, h ->
				assertTrue(h instanceof IntroductionRequest)
		);

		// After travelling in time one more 1ms, the introduction should be
		// auto-declined. We should get an event signalling that the response
		// has been sent.
		IntroductionResponseReceivedEvent e = assertEvent(c1,
				IntroductionResponseReceivedEvent.class, () ->
						timeTravel(c1, 1)
		);
		// the event should have correct values
		assertEquals(contactId0From1, e.getContactId());
		IntroductionResponse response = e.getMessageHeader();
		assertEquals(author2.getName(),
				response.getIntroducedAuthor().getName());
		assertTrue(response.isAutoDecline());

		// these should not have changed
		assertGroupCountAt0With1(1, 0);
		assertGroupCountAt0With2(1, 0);
		assertGroupCountAt2With0(1, 1);
		// there is still 1 message at 1 with 0, but it should now be the new
		// auto-decline message instead of the introduction
		assertGroupCountAt1With0(1, 0);
		forEachHeader(c1, contactId0From1, 1, h -> {
			assertTrue(h instanceof IntroductionResponse);
			IntroductionResponse r = (IntroductionResponse) h;
			assertEquals(author2.getName(), r.getIntroducedAuthor().getName());
			assertTrue(r.isAutoDecline());
		});

		// sync auto-decline to 0
		sync1To0(1, true);
		waitForEvents(c0);
		// auto-decline arrived at 0
		assertGroupCountAt0With1(2, 1);

		// forward auto-decline to 2
		sync0To2(1, true);
		waitForEvents(c2);
		// auto-decline arrived at 2
		assertGroupCountAt2With0(2, 2);
	}

	/**
	 * Let one introducee accept, the other decline manually
	 * ASSERT accept and decline messages arrive and have timer on all sides
	 * ASSERT accept and decline get forwarded to the other introducee and that
	 * they all have timers set on all sides
	 * ASSERT that all messages self-destruct
	 */
	@Test
	public void testIntroductionSessionManualDecline() throws Exception {
		makeIntroduction(true, true);

		assertIntroducerStatus(AWAIT_RESPONSES);

		// mark messages as read on 1 and 2, this starts 1's and 2's timer for 0
		markMessagesRead(c1, contact0From1);
		markMessagesRead(c2, contact0From2);
		assertGroupCountAt1With0(1, 0);
		assertGroupCountAt2With0(1, 0);

		respondToMostRecentIntroduction(c1, contactId0From1, true);
		respondToMostRecentIntroduction(c2, contactId0From2, false);
		sync1To0(1, true);
		sync2To0(1, true);
		waitForEvents(c0);

		// added the own responses
		assertGroupCountAt1With0(2, 0);
		assertGroupCountAt2With0(2, 0);

		// 0 has the sent introduction and the unread responses
		assertGroupCountAt0With1(2, 1);
		assertGroupCountAt0With2(2, 1);

		assertIntroducerStatus(START);

		markMessagesRead(c0, contact1From0);
		markMessagesRead(c0, contact2From0);
		assertGroupCountAt0With1(2, 0);
		assertGroupCountAt0With2(2, 0);

		// forward responses from 0 to introducees
		sync0To1(1, true);
		sync0To2(1, true);
		waitForEvents(c1);
		waitForEvents(c2);

		// first contact receives the forwarded decline
		assertGroupCountAt1With0(3, 1);
		// second contact does not display an extra message because 2 declined
		// themselves
		assertGroupCountAt2With0(2, 0);

		markMessagesRead(c1, contact0From1);
		assertGroupCountAt1With0(3, 0);

		assertMessagesAmong0And1HaveTimerSet(2, 3);
		assertMessagesAmong0And2HaveTimerSet(2, 2);

		// Travel in time on all devices
		timeTravel(c0);
		timeTravel(c1);
		timeTravel(c2);

		assertGroupCountAt0With1(0, 0);
		assertGroupCountAt0With2(0, 0);
		assertGroupCountAt1With0(0, 0);
		assertGroupCountAt2With0(0, 0);
	}

	/**
	 * Go through two full cycles of introducing two contacts with
	 * self-destructing messages enabled, lettings them both introducees expire
	 * their introductions, thereby auto-declining it.
	 */
	@Test
	public void testTwoIntroductionCycles() throws Exception {
		// FIRST CYCLE
		introduceAndAutoDecline();

		// SECOND CYCLE
		introduceAndAutoDecline();
	}

	private void introduceAndAutoDecline() throws Exception {
		// send introduction
		makeIntroduction(true, true);
		markMessagesRead(c1, contact0From1);
		markMessagesRead(c2, contact0From2);
		assertGroupCounts(1, 0, 1, 0, 1, 0, 1, 0);

		// ack from 1 and 2 to 0. This starts 0's timer
		ack1To0(1);
		ack2To0(1);
		waitForEvents(c0);

		// time travel on all devices, destroying the introductions at 0 and
		// making 1 and 2 auto-decline
		timeTravel(c0);
		timeTravel(c1);
		timeTravel(c2);
		assertGroupCounts(0, 0, 0, 0, 1, 0, 1, 0);

		// sync the auto-decline messages to 0
		sync1To0(1, true);
		sync2To0(1, true);
		waitForEvents(c0);
		assertGroupCounts(1, 1, 1, 1, 1, 0, 1, 0);

		// mark declines read on 0, starting the timer there and let them expire
		markMessagesRead(c0, contact1From0);
		markMessagesRead(c0, contact2From0);
		timeTravel(c0);
		assertGroupCounts(0, 0, 0, 0, 1, 0, 1, 0);

		// sync responses to 1 and 2
		sync0To1(1, true);
		sync0To2(1, true);
		waitForEvents(c1);
		waitForEvents(c2);

		// ack the responses to 0 to clear the ack counts for subsequent cycles,
		// also starts the timer for the responses at 1 and 2
		ack1To0(1);
		ack2To0(1);
		waitForEvents(c0);
		assertGroupCounts(0, 0, 0, 0, 1, 0, 1, 0);

		// let timers for responses expire
		timeTravel(c1);
		timeTravel(c2);
		assertGroupCounts(0, 0, 0, 0, 0, 0, 0, 0);
	}

	private void assertGroupCounts(int count01, int unread01, int count02,
			int unread02, int count10, int unread10, int count20, int unread20)
			throws Exception {
		assertGroupCountAt0With1(count01, unread01);
		assertGroupCountAt0With2(count02, unread02);
		assertGroupCountAt1With0(count10, unread10);
		assertGroupCountAt2With0(count20, unread20);
	}

	/**
	 * Let introductions self-destruct at the introducer and one of the
	 * introducees
	 * ASSERT that auto-declines get sent and arrive
	 * ASSERT that auto-declines have the timer set
	 * ASSERT that declines get forwarded to other introducee
	 * ASSERT that forwarded declines have the timer set
	 * ASSERT that forwarded declines self-destruct
	 * ASSERT that a that a new introduction can succeed afterwards
	 */
	@Test
	public void testIntroductionSessionAutoDecline() throws Exception {
		makeIntroduction(true, false);

		assertIntroducerStatus(AWAIT_RESPONSES);

		// ack from 1 and 2 to 0. This starts 0's timer for 1
		ack1To0(1);
		ack2To0(1);
		waitForEvents(c0);

		// mark messages as read on 1 and 2, this starts 1's timer for 0
		markMessagesRead(c1, contact0From1);
		markMessagesRead(c2, contact0From2);
		assertGroupCountAt1With0(1, 0);
		assertGroupCountAt2With0(1, 0);

		// Travel in time on all devices
		timeTravel(c0);
		timeTravel(c1);
		timeTravel(c2);

		// assert that introductions have been deleted between 0 and 1 but not
		// between 0 and 2
		assertGroupCountAt0With1(0, 0);
		assertGroupCountAt0With2(1, 0);
		// there is now 1 message, the auto-decline message
		assertGroupCountAt1With0(1, 0);
		assertGroupCountAt2With0(1, 0);

		// sync the auto-decline message from 1 to 0
		sync1To0(1, true);
		waitForEvents(c0);

		// the auto-decline from 1 has arrived at 0
		assertGroupCountAt0With1(1, 1);

		// the session status has moved to A_DECLINED or B_DECLINED
		assertIntroducerStatusFirstDeclined();

		// sync the auto-decline message from 0 to 2
		sync0To2(1, true);
		waitForEvents(c2);
		// the auto-decline from 1 has arrived at 2
		assertGroupCountAt2With0(2, 1);

		// 0 and 1 still have the auto-decline message from 0
		assertGroupCountAt0With1(1, 1);
		assertGroupCountAt1With0(1, 0);

		// make sure the auto-decline has the timer set at 0 and 1
		assertMessagesAmong0And1HaveTimerSet(1, 1);

		// ack message from 0 to 1 and make sure it self-destructs on 1
		ack0To1(1);
		timeTravel(c1);
		assertGroupCountAt1With0(0, 0);

		// mark message read on 0 and make sure it self-destructs on 0
		markMessagesRead(c0, contact1From0);
		timeTravel(c0);
		assertGroupCountAt0With1(0, 0);

		// assert that a that a new introduction can succeed afterwards:
		// first decline from c2, assert we're in START state and then
		// make the new introdution
		respondToMostRecentIntroduction(c2, contactId0From2, false);
		sync2To0(1, true);
		waitForEvents(c0);
		sync0To1(1, true);
		waitForEvents(c1);

		assertIntroducerStatus(START);
		assertNewIntroductionSucceeds();
	}

	@Test
	public void testIntroductionAcceptHasTimer() throws Exception {
		testIntroductionResponseHasTimer(true);
	}

	@Test
	public void testIntroductionDeclineHasTimer() throws Exception {
		testIntroductionResponseHasTimer(false);
	}

	private void testIntroductionResponseHasTimer(boolean accept)
			throws Exception {
		makeIntroduction(true, false);
		assertIntroductionsArrived();

		// check that all messages have the timer set
		assertMessagesAmong0And1HaveTimerSet(1, 1);
		assertMessagesAmong0And2HaveTimerNotSet(1, 1);

		respondToMostRecentIntroduction(c1, contactId0From1, accept);
		sync1To0(1, true);
		waitForEvents(c0);

		// check that response has arrived
		assertGroupCountAt0With1(2, 1);
		assertGroupCountAt1With0(2, 1);
		assertMessagesAmong0And1HaveTimerSet(2, 2);
	}

	@Test
	public void testIntroductionAcceptSelfDestructs() throws Exception {
		testIntroductionResponseSelfDestructs(true);
	}

	@Test
	public void testIntroductionDeclineSelfDestructs() throws Exception {
		testIntroductionResponseSelfDestructs(false);
	}

	private void testIntroductionResponseSelfDestructs(boolean accept)
			throws Exception {
		makeIntroduction(true, false);
		assertIntroductionsArrived();

		assertMessagesAmong0And1HaveTimerSet(1, 1);
		assertMessagesAmong0And2HaveTimerNotSet(1, 1);

		respondToMostRecentIntroduction(c1, contactId0From1, accept);
		sync1To0(1, true);
		waitForEvents(c0);

		// Sync the ack to 1 - this starts 1's timer
		ack0To1(1);
		waitForEvents(c1);

		// check that response has arrived
		assertGroupCountAt0With1(2, 1);
		assertGroupCountAt1With0(2, 1);
		assertMessagesAmong0And1HaveTimerSet(2, 2);

		// mark messages as read on 1, this starts 1's timer for 0
		markMessagesRead(c1, contact0From1);
		assertGroupCountAt1With0(2, 0);

		// mark messages as read on 0, this starts 0's timer for 1
		markMessagesRead(c0, contact1From0);
		assertGroupCountAt0With1(2, 0);

		// Travel in time on all devices
		timeTravel(c0);
		timeTravel(c1);
		timeTravel(c2);

		// assert that introductions and responses have been deleted between
		// 0 and 1
		assertGroupCountAt0With1(0, 0);
		assertGroupCountAt1With0(0, 0);
	}

	/*
	 * Tests that checks whether an introduction can still succeed properly
	 * after the introduction self-destructed at the introducer.
	 */

	/**
	 * Let introductions self-destruct at the introducer only
	 * Let both introducees accept the introduction
	 * ASSERT that accept messages still get forwarded to the other introducer
	 * ASSERT that the introduction succeeds
	 * ASSERT all messages involved self-destruct eventually
	 */
	@Test
	public void testSucceedAfterIntroducerSelfDestructed() throws Exception {
		testSucceedAfterIntroducerSelfDestructed(false);
	}

	/**
	 * Variant of the above test that also auto-deletes the responses at each
	 * introducee received from the respective other introducee.
	 */
	@Test
	public void testSucceedAfterIntroducerAndResponsesSelfDestructed()
			throws Exception {
		testSucceedAfterIntroducerSelfDestructed(true);
	}

	private void testSucceedAfterIntroducerSelfDestructed(
			boolean autoDeleteResponsesBeforeSyncingAuthAndActivate)
			throws Exception {
		makeIntroduction(true, true);

		// ack from 1 and 2 to 0. This starts 0's timer for 1 and 2
		ack1To0(1);
		ack2To0(1);
		waitForEvents(c0);

		// Travel in time at 0
		timeTravel(c0);

		assertGroupCountAt0With1(0, 0);
		assertGroupCountAt0With2(0, 0);

		// -> introductions self-destructed at the introducer only

		// introducees have got the introduction
		assertGroupCountAt1With0(1, 1);
		assertGroupCountAt2With0(1, 1);

		respondToMostRecentIntroduction(c1, contactId0From1, true);
		respondToMostRecentIntroduction(c2, contactId0From2, true);
		sync1To0(1, true);
		sync2To0(1, true);
		waitForEvents(c0);

		// introducees have got the own response
		assertGroupCountAt1With0(2, 1);
		assertGroupCountAt2With0(2, 1);

		// sync forwarded ACCEPT messages to introducees
		sync0To1(1, true);
		sync0To2(1, true);
		waitForEvents(c1);
		waitForEvents(c2);

		if (autoDeleteResponsesBeforeSyncingAuthAndActivate) {
			assertGroupCountAt1With0(2, 1);
			assertGroupCountAt2With0(2, 1);

			markMessagesRead(c1, contact0From1);
			markMessagesRead(c2, contact0From2);

			assertGroupCountAt1With0(2, 0);
			assertGroupCountAt2With0(2, 0);

			// Travel in time at 1 and 2
			timeTravel(c1);
			timeTravel(c2);

			assertGroupCountAt1With0(0, 0);
			assertGroupCountAt2With0(0, 0);
		}

		syncAuthAndActivateMessages();

		assertIntroductionSucceeded();
	}

	/*
	 * Group of three tests that check whether an introduction can still fail
	 * properly after the introduction self-destructed at the introducer.
	 * <p>
	 * Let introductions self-destruct at the introducer only
	 * Variant 1: Let both introducees decline the introduction
	 * Variant 2: Let first introducee accept, second decline the introduction
	 * Variant 3: Let first introducee decline, second accept the introduction
	 * ASSERT that accept/decline messages still get forwarded to the other introducer
	 * ASSERT that the introduction does not succeed
	 * ASSERT that abort messages do get sent
	 * ASSERT all messages involved self-destruct eventually
	 * ASSERT that a new introduction can succeed afterwards
	 */

	@Test
	public void testFailAfterIntroducerSelfDestructedBothDecline()
			throws Exception {
		testFailAfterIntroducerSelfDestructed(false, false);
	}

	@Test
	public void testFailAfterIntroducerSelfDestructedFirstAccept()
			throws Exception {
		testFailAfterIntroducerSelfDestructed(true, false);
	}

	@Test
	public void testFailAfterIntroducerSelfDestructedSecondAccept()
			throws Exception {
		testFailAfterIntroducerSelfDestructed(false, true);
	}

	private void testFailAfterIntroducerSelfDestructed(boolean firstAccepts,
			boolean secondAccepts) throws Exception {
		assertFalse(firstAccepts && secondAccepts);

		makeIntroduction(true, true);

		// ack from 1 and 2 to 0. This starts 0's timer for 1 and 2
		ack1To0(1);
		ack2To0(1);
		waitForEvents(c0);

		// Travel in time at 0
		timeTravel(c0);

		assertGroupCountAt0With1(0, 0);
		assertGroupCountAt0With2(0, 0);

		// -> introductions self-destructed at the introducer only

		assertIntroducerStatus(AWAIT_RESPONSES);
		assertIntroduceeStatus(c1, IntroduceeState.AWAIT_RESPONSES);
		assertIntroduceeStatus(c2, IntroduceeState.AWAIT_RESPONSES);

		// introducees have got the introduction
		assertGroupCountAt1With0(1, 1);
		assertGroupCountAt2With0(1, 1);

		// first contact reads the introduction and responds
		markMessagesRead(c1, contact0From1);
		respondToMostRecentIntroduction(c1, contactId0From1, firstAccepts);
		sync1To0(1, true);
		waitForEvents(c0);

		if (firstAccepts) {
			assertIntroducerStatusFirstAccepted();
		} else {
			assertIntroducerStatusFirstDeclined();
		}

		// second contact reads the introduction and responds
		markMessagesRead(c2, contact0From2);
		respondToMostRecentIntroduction(c2, contactId0From2, secondAccepts);
		sync2To0(1, true);
		waitForEvents(c0);

		assertIntroducerStatus(START);

		// introducees have got the own response
		assertGroupCountAt1With0(2, 0);
		assertGroupCountAt2With0(2, 0);

		// sync forwarded ACCEPT/DECLINE messages to introducees
		sync0To1(1, true);
		waitForEvents(c1);
		sync0To2(1, true);
		waitForEvents(c2);

		assertIntroductionFailed();

		if (firstAccepts) {
			// one additional message, the other introducee's response
			assertGroupCountAt1With0(3, 1);
		} else {
			assertGroupCountAt1With0(2, 0);
		}
		if (secondAccepts) {
			// one additional message, the other introducee's response
			assertGroupCountAt2With0(3, 1);
		} else {
			assertGroupCountAt2With0(2, 0);
		}

		timeTravel(c1);
		timeTravel(c2);

		if (firstAccepts) {
			assertGroupCountAt1With0(1, 1);
		} else {
			assertGroupCountAt1With0(0, 0);
		}
		if (secondAccepts) {
			assertGroupCountAt2With0(1, 1);
		} else {
			assertGroupCountAt2With0(0, 0);
		}

		// -> if one of the introducees accepted, they still have got an unread
		// decline from the other introducee

		if (firstAccepts) {
			markMessagesRead(c1, contact0From1);
			timeTravel(c1);
			assertGroupCountAt1With0(0, 0);
		}
		if (secondAccepts) {
			markMessagesRead(c2, contact0From2);
			timeTravel(c2);
			assertGroupCountAt2With0(0, 0);
		}

		// make sure the introducees session status returned to START
		assertIntroduceeStatus(c1, IntroduceeState.START);
		assertIntroduceeStatus(c2, IntroduceeState.START);

		assertNewIntroductionSucceeds();
	}

	private void makeIntroduction(boolean enableTimer1, boolean enableTimer2)
			throws Exception {
		if (enableTimer1) {
			setAutoDeleteTimer(c0, contact1From0.getId(),
					MIN_AUTO_DELETE_TIMER_MS);
		}
		if (enableTimer2) {
			setAutoDeleteTimer(c0, contact2From0.getId(),
					MIN_AUTO_DELETE_TIMER_MS);
		}

		// make introduction
		c0.getIntroductionManager()
				.makeIntroduction(contact1From0, contact2From0, "Hi!");

		sync0To1(1, true);
		sync0To2(1, true);
		waitForEvents(c1);
		waitForEvents(c2);
	}

	private void respondToMostRecentIntroduction(
			BriarIntegrationTestComponent c, ContactId contactId,
			boolean accept) throws Exception {
		List<ConversationMessageHeader> headers =
				getMessageHeaders(c, contactId);
		Collections.reverse(headers);
		for (ConversationMessageHeader h : headers) {
			if (h instanceof IntroductionRequest) {
				IntroductionRequest ir = (IntroductionRequest) h;
				c.getIntroductionManager().respondToIntroduction(contactId,
						ir.getSessionId(), accept);
				return;
			}
		}
		fail("no introduction found");
	}

	private void markMessagesRead(BriarIntegrationTestComponent c,
			Contact contact) throws Exception {
		for (ConversationMessageHeader h : getMessageHeaders(c,
				contact.getId())) {
			markMessageRead(c, contact, h.getId());
		}
	}

	private void syncAuthAndActivateMessages() throws Exception {
		// sync first AUTH and its forward
		sync1To0(1, true);
		sync0To2(1, true);

		// sync second AUTH and its forward as well as the following ACTIVATE
		sync2To0(2, true);
		sync0To1(2, true);

		// sync second ACTIVATE and its forward
		sync1To0(1, true);
		sync0To2(1, true);
	}

	private void timeTravel(BriarIntegrationTestComponent c) throws Exception {
		long timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;
		timeTravel(c, timerLatency);
	}

	private void timeTravel(BriarIntegrationTestComponent c, long timerLatency)
			throws Exception {
		c.getTimeTravel().addCurrentTimeMillis(timerLatency);
		waitForEvents(c);
	}

	private void assertIntroductionsArrived() throws DbException {
		// check that introductions have arrived at the introducees
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertGroupCount(c0, contactId2From0, 1, 0);
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertGroupCount(c2, contactId0From2, 1, 1);
	}

	private void assertGroupCountAt0With1(int messageCount, int unreadCount)
			throws Exception {
		assertGroupCount(c0, contactId1From0, messageCount, unreadCount);
		assertEquals(messageCount,
				getMessageHeaders(c0, contactId1From0).size());
	}

	private void assertGroupCountAt0With2(int messageCount, int unreadCount)
			throws Exception {
		assertGroupCount(c0, contactId2From0, messageCount, unreadCount);
		assertEquals(messageCount,
				getMessageHeaders(c0, contactId2From0).size());
	}

	private void assertGroupCountAt1With0(int messageCount, int unreadCount)
			throws Exception {
		assertGroupCount(c1, contactId0From1, messageCount, unreadCount);
		assertEquals(messageCount,
				getMessageHeaders(c1, contactId0From1).size());
	}

	private void assertGroupCountAt2With0(int messageCount, int unreadCount)
			throws Exception {
		assertGroupCount(c2, contactId0From2, messageCount, unreadCount);
		assertEquals(messageCount,
				getMessageHeaders(c2, contactId0From2).size());
	}

	private void assertMessagesAmong0And1HaveTimerSet(int numC0, int numC1)
			throws Exception {
		forEachHeader(c0, contactId1From0, numC0, h ->
				assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer()));
		forEachHeader(c1, contactId0From1, numC1, h ->
				assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer()));
	}

	private void assertMessagesAmong0And2HaveTimerSet(int numC0, int numC2)
			throws Exception {
		forEachHeader(c0, contactId2From0, numC0, h ->
				assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer()));
		forEachHeader(c2, contactId0From2, numC2, h ->
				assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer()));
	}

	private void assertMessagesAmong0And2HaveTimerNotSet(int numC0, int numC2)
			throws Exception {
		forEachHeader(c0, contactId2From0, numC0, h ->
				assertEquals(NO_AUTO_DELETE_TIMER, h.getAutoDeleteTimer()));
		forEachHeader(c2, contactId0From2, numC2, h ->
				assertEquals(NO_AUTO_DELETE_TIMER, h.getAutoDeleteTimer()));
	}

	private void assertIntroducerStatus(IntroducerState state)
			throws DbException, FormatException {
		IntroducerSession introducerSession = getIntroducerSession();
		assertEquals(state, introducerSession.getState());
	}

	private void assertIntroducerStatusFirstDeclined()
			throws DbException, FormatException {
		IntroductionCrypto introductionCrypto =
				((IntroductionIntegrationTestComponent) c0)
						.getIntroductionCrypto();
		boolean alice =
				introductionCrypto.isAlice(contact1From0.getAuthor().getId(),
						contact2From0.getAuthor().getId());
		IntroducerSession introducerSession = getIntroducerSession();
		assertEquals(alice ? A_DECLINED : B_DECLINED,
				introducerSession.getState());
	}

	private void assertIntroducerStatusFirstAccepted()
			throws DbException, FormatException {
		IntroductionCrypto introductionCrypto =
				((IntroductionIntegrationTestComponent) c0)
						.getIntroductionCrypto();
		boolean alice =
				introductionCrypto.isAlice(contact1From0.getAuthor().getId(),
						contact2From0.getAuthor().getId());
		IntroducerSession introducerSession = getIntroducerSession();
		assertEquals(alice ? AWAIT_RESPONSE_B : AWAIT_RESPONSE_A,
				introducerSession.getState());
	}

	private void assertIntroduceeStatus(BriarIntegrationTestComponent c,
			IntroduceeState state)
			throws DbException, FormatException {
		IntroduceeSession introduceeSession = getIntroduceeSession(c);
		assertEquals(state, introduceeSession.getState());
	}

	private void assertIntroductionSucceeded() throws DbException {
		// make sure that introduced contacts have each other in their contact
		// manager
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
	}

	private void assertIntroductionFailed() throws DbException {
		// make sure that introduced contacts do not have each other in their
		// contact manager
		assertFalse(contactManager1
				.contactExists(author2.getId(), author1.getId()));
		assertFalse(contactManager2
				.contactExists(author1.getId(), author2.getId()));
	}

	private void assertNewIntroductionSucceeds() throws Exception {
		makeIntroduction(false, false);

		respondToMostRecentIntroduction(c1, contactId0From1, true);
		respondToMostRecentIntroduction(c2, contactId0From2, true);
		sync1To0(1, true);
		sync2To0(1, true);
		waitForEvents(c0);

		// forward responses from 0 to introducees
		sync0To1(1, true);
		sync0To2(1, true);
		waitForEvents(c1);
		waitForEvents(c2);

		syncAuthAndActivateMessages();

		assertIntroductionSucceeded();
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

	private IntroducerSession getIntroducerSession()
			throws DbException, FormatException {
		Map<MessageId, BdfDictionary> dicts = c0.getClientHelper()
				.getMessageMetadataAsDictionary(getLocalGroup().getId());
		assertEquals(1, dicts.size());
		BdfDictionary d = dicts.values().iterator().next();
		SessionParser sessionParser =
				((IntroductionIntegrationTestComponent) c0).getSessionParser();
		return sessionParser.parseIntroducerSession(d);
	}

	private IntroduceeSession getIntroduceeSession(
			BriarIntegrationTestComponent c)
			throws DbException, FormatException {
		Map<MessageId, BdfDictionary> dicts = c.getClientHelper()
				.getMessageMetadataAsDictionary(getLocalGroup().getId());
		assertEquals(1, dicts.size());
		BdfDictionary d = dicts.values().iterator().next();
		Group introducerGroup =
				c2.getIntroductionManager().getContactGroup(contact0From2);
		SessionParser sessionParser =
				((IntroductionIntegrationTestComponent) c).getSessionParser();
		return sessionParser
				.parseIntroduceeSession(introducerGroup.getId(), d);
	}

	private Group getLocalGroup() {
		return contactGroupFactory
				.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
	}

}
