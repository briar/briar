package org.briarproject.bramble.test;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.BdfStringUtils;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.event.MessageStateChangedEvent;
import org.briarproject.bramble.api.sync.event.MessagesAckedEvent;
import org.briarproject.bramble.api.sync.event.MessagesSentEvent;
import org.junit.After;
import org.junit.Before;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.sync.validation.MessageState.DELIVERED;
import static org.briarproject.bramble.api.sync.validation.MessageState.INVALID;
import static org.briarproject.bramble.api.sync.validation.MessageState.PENDING;
import static org.briarproject.bramble.test.TestPluginConfigModule.SIMPLEX_TRANSPORT_ID;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class BrambleIntegrationTest<C extends BrambleIntegrationTestComponent>
		extends BrambleTestCase {

	private static final Logger LOG =
			getLogger(BrambleIntegrationTest.class.getName());

	private static final boolean DEBUG = false;

	protected final static int TIMEOUT = 15000;

	// objects accessed from background threads need to be volatile
	private volatile Waiter validationWaiter;
	private volatile Waiter deliveryWaiter;
	private volatile Waiter ackWaiter;
	private volatile boolean expectAck = false;

	private final Semaphore messageSemaphore = new Semaphore(0);
	private final AtomicInteger deliveryCounter = new AtomicInteger(0);
	private final AtomicInteger validationCounter = new AtomicInteger(0);
	private final AtomicInteger ackCounter = new AtomicInteger(0);

	protected final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() throws Exception {
		assertTrue(testDir.mkdirs());

		// initialize waiters fresh for each test
		validationWaiter = new Waiter();
		deliveryWaiter = new Waiter();
		ackWaiter = new Waiter();
		deliveryCounter.set(0);
		validationCounter.set(0);
		ackCounter.set(0);
	}

	@After
	public void tearDown() throws Exception {
		TestUtils.deleteTestDirectory(testDir);
	}

	protected void addEventListener(C c) {
		c.getEventBus().addListener(new Listener(c));
	}

	private class Listener implements EventListener {

		private final ClientHelper clientHelper;
		private final Executor executor;

		private Listener(C c) {
			clientHelper = c.getClientHelper();
			executor = newSingleThreadExecutor();
		}

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent event = (MessageStateChangedEvent) e;
				if (!event.isLocal()) {
					if (event.getState() == DELIVERED) {
						LOG.info("Delivered new message "
								+ event.getMessageId());
						deliveryCounter.addAndGet(1);
						loadAndLogMessage(event.getMessageId());
						deliveryWaiter.resume();
					} else if (event.getState() == INVALID ||
							event.getState() == PENDING) {
						LOG.info("Validated new " + event.getState().name() +
								" message " + event.getMessageId());
						validationCounter.addAndGet(1);
						loadAndLogMessage(event.getMessageId());
						validationWaiter.resume();
					}
				}
			} else if (e instanceof MessagesAckedEvent && expectAck) {
				MessagesAckedEvent event = (MessagesAckedEvent) e;
				ackCounter.addAndGet(event.getMessageIds().size());
				for (MessageId m : event.getMessageIds()) {
					loadAndLogMessage(m);
					ackWaiter.resume();
				}
			}
		}

		private void loadAndLogMessage(MessageId id) {
			executor.execute(() -> {
				if (DEBUG) {
					try {
						BdfList body = clientHelper.getMessageAsList(id);
						LOG.info("Contents of " + id + ":\n"
								+ BdfStringUtils.toString(body));
					} catch (DbException | FormatException e) {
						logException(LOG, WARNING, e);
					}
				}
				messageSemaphore.release();
			});
		}
	}


	protected void syncMessage(BrambleIntegrationTestComponent fromComponent,
			BrambleIntegrationTestComponent toComponent, ContactId toId,
			int num, boolean valid) throws Exception {
		syncMessage(fromComponent, toComponent, toId, num, 0, valid ? 0 : num,
				valid ? num : 0);
	}

	protected void syncMessage(BrambleIntegrationTestComponent fromComponent,
			BrambleIntegrationTestComponent toComponent, ContactId toId,
			int numNew, int numDupes, int numPendingOrInvalid, int numDelivered)
			throws Exception {

		// Debug output
		String from =
				fromComponent.getIdentityManager().getLocalAuthor().getName();
		String to = toComponent.getIdentityManager().getLocalAuthor().getName();
		LOG.info("TEST: Sending " + (numNew + numDupes) + " message(s) from "
				+ from + " to " + to);

		// Listen for messages being sent
		waitForEvents(fromComponent);
		SendListener sendListener = new SendListener();
		fromComponent.getEventBus().addListener(sendListener);

		// Write the messages to a transport stream
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestTransportConnectionWriter writer =
				new TestTransportConnectionWriter(out, false);
		fromComponent.getConnectionManager().manageOutgoingConnection(toId,
				SIMPLEX_TRANSPORT_ID, writer);
		writer.getDisposedLatch().await(TIMEOUT, MILLISECONDS);

		// Check that the expected number of messages were sent
		waitForEvents(fromComponent);
		fromComponent.getEventBus().removeListener(sendListener);
		assertEquals("Messages sent", numNew + numDupes,
				sendListener.sent.size());

		// Read the messages from the transport stream
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		TestTransportConnectionReader reader =
				new TestTransportConnectionReader(in);
		toComponent.getConnectionManager().manageIncomingConnection(
				SIMPLEX_TRANSPORT_ID, reader);

		if (numPendingOrInvalid > 0) {
			validationWaiter.await(TIMEOUT, numPendingOrInvalid);
		}
		assertEquals("Messages validated", numPendingOrInvalid,
				validationCounter.getAndSet(0));

		if (numDelivered > 0) {
			deliveryWaiter.await(TIMEOUT, numDelivered);
		}
		assertEquals("Messages delivered", numDelivered,
				deliveryCounter.getAndSet(0));

		try {
			messageSemaphore.tryAcquire(numNew, TIMEOUT, MILLISECONDS);
		} catch (InterruptedException e) {
			LOG.info("Interrupted while waiting for messages");
			Thread.currentThread().interrupt();
			fail();
		}
	}

	protected void sendAcks(BrambleIntegrationTestComponent fromComponent,
			BrambleIntegrationTestComponent toComponent, ContactId toId,
			int num) throws Exception {
		// Debug output
		String from =
				fromComponent.getIdentityManager().getLocalAuthor().getName();
		String to = toComponent.getIdentityManager().getLocalAuthor().getName();
		LOG.info("TEST: Sending " + num + " ACKs from " + from + " to " + to);

		expectAck = true;

		// Listen for messages being sent (none should be sent)
		waitForEvents(fromComponent);
		SendListener sendListener = new SendListener();
		fromComponent.getEventBus().addListener(sendListener);

		// start outgoing connection
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestTransportConnectionWriter writer =
				new TestTransportConnectionWriter(out, false);
		fromComponent.getConnectionManager().manageOutgoingConnection(toId,
				SIMPLEX_TRANSPORT_ID, writer);
		writer.getDisposedLatch().await(TIMEOUT, MILLISECONDS);

		// Check that no messages were sent
		waitForEvents(fromComponent);
		fromComponent.getEventBus().removeListener(sendListener);
		assertEquals("Messages sent", 0, sendListener.sent.size());

		// handle incoming connection
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		TestTransportConnectionReader reader =
				new TestTransportConnectionReader(in);
		toComponent.getConnectionManager().manageIncomingConnection(
				SIMPLEX_TRANSPORT_ID, reader);

		ackWaiter.await(TIMEOUT, num);
		assertEquals("ACKs delivered", num, ackCounter.getAndSet(0));
		assertEquals("No messages delivered", 0, deliveryCounter.get());
		try {
			messageSemaphore.tryAcquire(num, TIMEOUT, MILLISECONDS);
		} catch (InterruptedException e) {
			LOG.info("Interrupted while waiting for messages");
			Thread.currentThread().interrupt();
			fail();
		} finally {
			expectAck = false;
		}
	}

	/**
	 * Broadcasts a marker event and waits for it to be delivered, which
	 * indicates that all previously broadcast events have been delivered.
	 */
	public static void waitForEvents(BrambleIntegrationTestComponent component)
			throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		MarkerEvent marker = new MarkerEvent();
		EventBus eventBus = component.getEventBus();
		eventBus.addListener(new EventListener() {
			@Override
			public void eventOccurred(@Nonnull Event e) {
				if (e == marker) {
					latch.countDown();
					eventBus.removeListener(this);
				}
			}
		});
		eventBus.broadcast(marker);
		if (!latch.await(1, MINUTES)) fail();
	}

	private static class MarkerEvent extends Event {
	}

	private static class SendListener implements EventListener {

		private final Set<MessageId> sent = new HashSet<>();

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessagesSentEvent) {
				sent.addAll(((MessagesSentEvent) e).getMessageIds());
			}
		}
	}
}
