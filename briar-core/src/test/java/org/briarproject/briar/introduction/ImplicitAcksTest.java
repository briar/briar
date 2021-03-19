package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
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
import static org.briarproject.briar.api.introduction.IntroductionManager.CLIENT_ID;
import static org.briarproject.briar.api.introduction.IntroductionManager.MAJOR_VERSION;
import static org.briarproject.briar.introduction.IntroducerState.AWAIT_RESPONSES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ImplicitAcksTest extends AbstractAutoDeleteTest {

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

	@Test
	public void testNoAck() throws Exception {
		makeIntroduction(true, true);

		assertIntroducerStatus(AWAIT_RESPONSES);

		timeTravel(c0);

		// no ack, no response, the messages do not self-destruct
		assertGroupCountAt0With1(1, 0);
		assertGroupCountAt0With2(1, 0);
	}

	@Test
	public void testExplicitAck() throws Exception {
		makeIntroduction(true, true);

		assertIntroducerStatus(AWAIT_RESPONSES);

		// Sync the ack to 0 from 1 and 2 - this starts 0's timer
		ack1To0(1);
		ack2To0(1);
		waitForEvents(c0);

		timeTravel(c0);

		// the introduction has self-destructed
		assertGroupCountAt0With1(0, 0);
		assertGroupCountAt0With2(0, 0);
	}

	/**
	 * Test that responding to an introduction and syncing the response also
	 * triggers the timer to start running at the introducer's side.
	 */
	@Test
	public void testImplicitAck() throws Exception {
		makeIntroduction(true, true);

		assertIntroducerStatus(AWAIT_RESPONSES);

		// responding to the invitation, this starts 0's timer for the
		// introduction
		respondToMostRecentIntroduction(c1, contactId0From1, true);
		respondToMostRecentIntroduction(c2, contactId0From2, false);
		sync1To0(1, true);
		sync2To0(1, true);
		waitForEvents(c0);

		// 0 has the sent introduction and the unread responses
		assertGroupCountAt0With1(2, 1);
		assertGroupCountAt0With2(2, 1);

		timeTravel(c0);

		// 0 has only the received responses, the introduction has
		// self-destructed
		assertGroupCountAt0With1(1, 1);
		assertGroupCountAt0With2(1, 1);

		// the one message we have left is the response, not the introduction
		forEachHeader(c0, contactId1From0, 1, h -> {
			assertTrue(h instanceof IntroductionResponse);
		});
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

	private void timeTravel(BriarIntegrationTestComponent c) throws Exception {
		long timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;
		timeTravel(c, timerLatency);
	}

	private void timeTravel(BriarIntegrationTestComponent c, long timerLatency)
			throws Exception {
		c.getTimeTravel().addCurrentTimeMillis(timerLatency);
		waitForEvents(c);
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

	private void assertIntroducerStatus(IntroducerState state)
			throws DbException, FormatException {
		IntroducerSession introducerSession = getIntroducerSession();
		assertEquals(state, introducerSession.getState());
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

	private Group getLocalGroup() {
		return contactGroupFactory
				.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
	}

}
