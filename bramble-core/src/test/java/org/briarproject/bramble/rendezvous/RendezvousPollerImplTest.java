package org.briarproject.bramble.rendezvous;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactState;
import org.briarproject.bramble.api.contact.event.PendingContactAddedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.plugin.ConnectionHandler;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.plugin.event.TransportInactiveEvent;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;
import org.briarproject.bramble.api.rendezvous.RendezvousEndpoint;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionClosedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionOpenedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousPollEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.api.system.TaskScheduler.Cancellable;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.briarproject.bramble.test.PredicateMatcher;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.api.contact.PendingContactState.ADDING_CONTACT;
import static org.briarproject.bramble.api.contact.PendingContactState.FAILED;
import static org.briarproject.bramble.api.contact.PendingContactState.OFFLINE;
import static org.briarproject.bramble.api.contact.PendingContactState.WAITING_FOR_CONNECTION;
import static org.briarproject.bramble.rendezvous.RendezvousConstants.POLLING_INTERVAL_MS;
import static org.briarproject.bramble.rendezvous.RendezvousConstants.RENDEZVOUS_TIMEOUT_MS;
import static org.briarproject.bramble.test.CollectionMatcher.collectionOf;
import static org.briarproject.bramble.test.PairMatcher.pairOf;
import static org.briarproject.bramble.test.TestUtils.getAgreementPrivateKey;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getPendingContact;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.briarproject.bramble.test.TestUtils.getTransportProperties;

public class RendezvousPollerImplTest extends BrambleMockTestCase {

	private final TaskScheduler scheduler = context.mock(TaskScheduler.class);
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final IdentityManager identityManager =
			context.mock(IdentityManager.class);
	private final TransportCrypto transportCrypto =
			context.mock(TransportCrypto.class);
	private final RendezvousCrypto rendezvousCrypto =
			context.mock(RendezvousCrypto.class);
	private final PluginManager pluginManager =
			context.mock(PluginManager.class);
	private final ConnectionManager connectionManager =
			context.mock(ConnectionManager.class);
	private final EventBus eventBus = context.mock(EventBus.class);
	private final Clock clock = context.mock(Clock.class);
	private final DuplexPlugin plugin = context.mock(DuplexPlugin.class);
	private final KeyMaterialSource keyMaterialSource =
			context.mock(KeyMaterialSource.class);
	private final RendezvousEndpoint rendezvousEndpoint =
			context.mock(RendezvousEndpoint.class);
	private final Cancellable cancellable = context.mock(Cancellable.class);

	private final Executor ioExecutor = new ImmediateExecutor();
	private final PendingContact pendingContact = getPendingContact();
	private final KeyPair handshakeKeyPair =
			new KeyPair(getAgreementPublicKey(), getAgreementPrivateKey());
	private final SecretKey staticMasterKey = getSecretKey();
	private final SecretKey rendezvousKey = getSecretKey();
	private final TransportId transportId = getTransportId();
	private final TransportProperties transportProperties =
			getTransportProperties(3);
	private final boolean alice = new Random().nextBoolean();

	private RendezvousPollerImpl rendezvousPoller;

	@Before
	public void setUp() {
		rendezvousPoller = new RendezvousPollerImpl(ioExecutor, scheduler, db,
				identityManager, transportCrypto, rendezvousCrypto,
				pluginManager, connectionManager, eventBus, clock);
	}

	@Test
	public void testAddsPendingContactsAndSchedulesPollingAtStartup()
			throws Exception {
		Transaction txn = new Transaction(null, true);
		long beforeExpiry = pendingContact.getTimestamp()
				+ RENDEZVOUS_TIMEOUT_MS - 1000;
		long afterExpiry = beforeExpiry + POLLING_INTERVAL_MS;
		AtomicReference<Runnable> capturePollTask;

		// Start the service
		context.checking(new DbExpectations() {{
			// Load the pending contacts
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(db).getPendingContacts(txn);
			will(returnValue(singletonList(pendingContact)));
			// The pending contact has not expired
			oneOf(clock).currentTimeMillis();
			will(returnValue(beforeExpiry));
			oneOf(eventBus).broadcast(with(new PredicateMatcher<>(
					PendingContactStateChangedEvent.class, e ->
					e.getPendingContactState() == OFFLINE)));
		}});

		expectDeriveRendezvousKey();
		capturePollTask = expectSchedulePolling();

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		// Run the poll task - pending contact expires, polling is cancelled
		expectPendingContactExpires(afterExpiry);
		expectCancelPolling();

		capturePollTask.get().run();
	}

	@Test
	public void testExpiresPendingContactAtStartup() throws Exception {
		Transaction txn = new Transaction(null, true);
		long atExpiry = pendingContact.getTimestamp() + RENDEZVOUS_TIMEOUT_MS;

		// Start the service
		context.checking(new DbExpectations() {{
			// Load the pending contacts
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(db).getPendingContacts(txn);
			will(returnValue(singletonList(pendingContact)));
			// The pending contact has already expired
			oneOf(clock).currentTimeMillis();
			will(returnValue(atExpiry));
			oneOf(eventBus).broadcast(with(new PredicateMatcher<>(
					PendingContactStateChangedEvent.class, e ->
					e.getPendingContactState() == FAILED)));
		}});

		rendezvousPoller.startService();
	}

	@Test
	public void testCreatesAndClosesEndpointsWhenPendingContactIsAddedAndRemoved()
			throws Exception {
		long beforeExpiry = pendingContact.getTimestamp();

		// Start the service
		expectStartupWithNoPendingContacts();

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		// Activate the transport - no endpoints should be created yet
		expectGetPlugin();

		rendezvousPoller.eventOccurred(new TransportActiveEvent(transportId));
		context.assertIsSatisfied();

		// Add the pending contact - endpoint should be created and polled,
		// polling should be scheduled
		expectAddPendingContact(beforeExpiry, WAITING_FOR_CONNECTION);
		expectDeriveRendezvousKey();
		expectCreateEndpoint();

		context.checking(new Expectations() {{
			// Poll newly added pending contact
			oneOf(rendezvousEndpoint).getRemoteTransportProperties();
			will(returnValue(transportProperties));
			oneOf(clock).currentTimeMillis();
			will(returnValue(beforeExpiry));
			oneOf(eventBus).broadcast(with(any(RendezvousPollEvent.class)));
			oneOf(plugin).poll(with(collectionOf(pairOf(
					equal(transportProperties),
					any(ConnectionHandler.class)))));
		}});

		expectSchedulePolling();

		rendezvousPoller.eventOccurred(
				new PendingContactAddedEvent(pendingContact));
		context.assertIsSatisfied();

		// Remove the pending contact - endpoint should be closed,
		// polling should be cancelled
		expectCloseEndpoint();
		expectCancelPolling();

		rendezvousPoller.eventOccurred(
				new PendingContactRemovedEvent(pendingContact.getId()));
		context.assertIsSatisfied();

		// Deactivate the transport - endpoint is already closed
		rendezvousPoller.eventOccurred(new TransportInactiveEvent(transportId));
	}

	@Test
	public void testCreatesAndClosesEndpointsWhenPendingContactIsAddedAndExpired()
			throws Exception {
		long beforeExpiry = pendingContact.getTimestamp()
				+ RENDEZVOUS_TIMEOUT_MS - 1000;
		long afterExpiry = beforeExpiry + POLLING_INTERVAL_MS;
		AtomicReference<Runnable> capturePollTask;

		// Start the service
		expectStartupWithNoPendingContacts();

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		// Activate the transport - no endpoints should be created yet
		expectGetPlugin();

		rendezvousPoller.eventOccurred(new TransportActiveEvent(transportId));
		context.assertIsSatisfied();

		// Add the pending contact - endpoint should be created and polled,
		// polling should be scheduled
		expectAddPendingContact(beforeExpiry, WAITING_FOR_CONNECTION);
		expectDeriveRendezvousKey();
		expectCreateEndpoint();

		context.checking(new Expectations() {{
			// Poll newly added pending contact
			oneOf(rendezvousEndpoint).getRemoteTransportProperties();
			will(returnValue(transportProperties));
			oneOf(clock).currentTimeMillis();
			will(returnValue(beforeExpiry));
			oneOf(eventBus).broadcast(with(any(RendezvousPollEvent.class)));
			oneOf(plugin).poll(with(collectionOf(pairOf(
					equal(transportProperties),
					any(ConnectionHandler.class)))));
		}});

		capturePollTask = expectSchedulePolling();

		rendezvousPoller.eventOccurred(
				new PendingContactAddedEvent(pendingContact));
		context.assertIsSatisfied();

		// Run the poll task - pending contact expires, endpoint is closed,
		// polling is cancelled
		expectPendingContactExpires(afterExpiry);
		expectCloseEndpoint();
		expectCancelPolling();

		capturePollTask.get().run();
		context.assertIsSatisfied();

		// Remove the pending contact - endpoint is already closed
		rendezvousPoller.eventOccurred(
				new PendingContactRemovedEvent(pendingContact.getId()));
		context.assertIsSatisfied();

		// Deactivate the transport - endpoint is already closed
		rendezvousPoller.eventOccurred(new TransportInactiveEvent(transportId));
	}

	@Test
	public void testCreatesAndClosesEndpointsWhenTransportIsActivatedAndDeactivated()
			throws Exception {
		long beforeExpiry = pendingContact.getTimestamp();

		// Start the service
		expectStartupWithNoPendingContacts();

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		// Add the pending contact - no endpoints should be created yet
		expectAddPendingContact(beforeExpiry, OFFLINE);
		expectDeriveRendezvousKey();
		expectSchedulePolling();

		rendezvousPoller.eventOccurred(
				new PendingContactAddedEvent(pendingContact));
		context.assertIsSatisfied();

		// Activate the transport - endpoint should be created
		expectGetPlugin();
		expectCreateEndpoint();
		expectStateChangedEvent(WAITING_FOR_CONNECTION);

		rendezvousPoller.eventOccurred(new TransportActiveEvent(transportId));
		context.assertIsSatisfied();

		// Deactivate the transport - endpoint should be closed
		expectCloseEndpoint();
		expectStateChangedEvent(OFFLINE);

		rendezvousPoller.eventOccurred(new TransportInactiveEvent(transportId));
		context.assertIsSatisfied();

		// Remove the pending contact - endpoint is already closed
		expectCancelPolling();

		rendezvousPoller.eventOccurred(
				new PendingContactRemovedEvent(pendingContact.getId()));
	}

	@Test
	public void testRendezvousConnectionEvents() throws Exception {
		long beforeExpiry = pendingContact.getTimestamp();

		// Start the service
		expectStartupWithPendingContact(beforeExpiry);

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		// Connection is opened - event should be broadcast
		expectStateChangedEvent(ADDING_CONTACT);

		rendezvousPoller.eventOccurred(
				new RendezvousConnectionOpenedEvent(pendingContact.getId()));
		context.assertIsSatisfied();

		// Connection fails - event should be broadcast
		expectStateChangedEvent(WAITING_FOR_CONNECTION);

		rendezvousPoller.eventOccurred(new RendezvousConnectionClosedEvent(
				pendingContact.getId(), false));
	}

	@Test
	public void testPendingContactExpiresBeforeConnection() throws Exception {
		long beforeExpiry = pendingContact.getTimestamp()
				+ RENDEZVOUS_TIMEOUT_MS - 1000;
		long afterExpiry = beforeExpiry + POLLING_INTERVAL_MS;

		// Start the service, capturing the poll task
		AtomicReference<Runnable> capturePollTask =
				expectStartupWithPendingContact(beforeExpiry);

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		// Run the poll task - pending contact expires, polling is cancelled
		expectPendingContactExpires(afterExpiry);
		expectCancelPolling();

		capturePollTask.get().run();
		context.assertIsSatisfied();

		// Connection is opened - no event should be broadcast
		rendezvousPoller.eventOccurred(
				new RendezvousConnectionOpenedEvent(pendingContact.getId()));
		context.assertIsSatisfied();

		// Connection fails - no event should be broadcast
		rendezvousPoller.eventOccurred(new RendezvousConnectionClosedEvent(
				pendingContact.getId(), false));
	}

	@Test
	public void testPendingContactExpiresDuringFailedConnection()
			throws Exception {
		long beforeExpiry = pendingContact.getTimestamp()
				+ RENDEZVOUS_TIMEOUT_MS - 1000;
		long afterExpiry = beforeExpiry + POLLING_INTERVAL_MS;

		// Start the service, capturing the poll task
		AtomicReference<Runnable> capturePollTask =
				expectStartupWithPendingContact(beforeExpiry);

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		// Connection is opened - event should be broadcast
		expectStateChangedEvent(ADDING_CONTACT);

		rendezvousPoller.eventOccurred(
				new RendezvousConnectionOpenedEvent(pendingContact.getId()));
		context.assertIsSatisfied();

		// Run the poll task - pending contact expires, polling is cancelled
		expectPendingContactExpires(afterExpiry);
		expectCancelPolling();

		capturePollTask.get().run();
		context.assertIsSatisfied();

		// Connection fails - no event should be broadcast
		rendezvousPoller.eventOccurred(new RendezvousConnectionClosedEvent(
				pendingContact.getId(), false));
	}

	@Test
	public void testPendingContactExpiresDuringSuccessfulConnection()
			throws Exception {
		long beforeExpiry = pendingContact.getTimestamp()
				+ RENDEZVOUS_TIMEOUT_MS - 1000;
		long afterExpiry = beforeExpiry + POLLING_INTERVAL_MS;

		// Start the service, capturing the poll task
		AtomicReference<Runnable> capturePollTask =
				expectStartupWithPendingContact(beforeExpiry);

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		// Connection is opened - event should be broadcast
		expectStateChangedEvent(ADDING_CONTACT);

		rendezvousPoller.eventOccurred(
				new RendezvousConnectionOpenedEvent(pendingContact.getId()));
		context.assertIsSatisfied();

		// Run the poll task - pending contact expires, polling is cancelled
		expectPendingContactExpires(afterExpiry);
		expectCancelPolling();

		capturePollTask.get().run();
		context.assertIsSatisfied();

		// Pending contact is removed - no event should be broadcast
		rendezvousPoller.eventOccurred(
				new PendingContactRemovedEvent(pendingContact.getId()));
	}

	@Test
	public void testPendingContactRemovedDuringFailedConnection()
			throws Exception {
		long beforeExpiry = pendingContact.getTimestamp();

		// Start the service
		expectStartupWithPendingContact(beforeExpiry);

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		// Connection is opened - event should be broadcast
		expectStateChangedEvent(ADDING_CONTACT);

		rendezvousPoller.eventOccurred(
				new RendezvousConnectionOpenedEvent(pendingContact.getId()));
		context.assertIsSatisfied();

		// Pending contact is removed - no event should be broadcast
		expectCancelPolling();

		rendezvousPoller.eventOccurred(
				new PendingContactRemovedEvent(pendingContact.getId()));
		context.assertIsSatisfied();

		// Connection fails - no event should be broadcast
		rendezvousPoller.eventOccurred(new RendezvousConnectionClosedEvent(
				pendingContact.getId(), false));
	}

	private AtomicReference<Runnable> expectSchedulePolling() {
		AtomicReference<Runnable> capturePollTask = new AtomicReference<>();

		context.checking(new Expectations() {{
			oneOf(scheduler).scheduleWithFixedDelay(with(any(Runnable.class)),
					with(any(Executor.class)), with(POLLING_INTERVAL_MS),
					with(POLLING_INTERVAL_MS), with(MILLISECONDS));
			will(doAll(new CaptureArgumentAction<>(capturePollTask,
					Runnable.class, 0), returnValue(cancellable)));
		}});

		return capturePollTask;
	}

	private void expectCancelPolling() {
		context.checking(new Expectations() {{
			oneOf(cancellable).cancel();
		}});
	}

	private void expectStartupWithNoPendingContacts() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			// Load the pending contacts
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(db).getPendingContacts(txn);
			will(returnValue(emptyList()));
		}});
	}

	private void expectAddPendingContact(long now,
			PendingContactState initialState) {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(eventBus).broadcast(with(new PredicateMatcher<>(
					PendingContactStateChangedEvent.class, e ->
					e.getPendingContactState() == initialState)));
		}});
	}

	private void expectDeriveRendezvousKey() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			will(returnValue(handshakeKeyPair));
			oneOf(transportCrypto).deriveStaticMasterKey(
					pendingContact.getPublicKey(), handshakeKeyPair);
			will(returnValue(staticMasterKey));
			oneOf(rendezvousCrypto).deriveRendezvousKey(staticMasterKey);
			will(returnValue(rendezvousKey));
			oneOf(transportCrypto).isAlice(pendingContact.getPublicKey(),
					handshakeKeyPair);
			will(returnValue(alice));
		}});
	}

	private void expectCreateEndpoint() {
		context.checking(new Expectations() {{
			oneOf(rendezvousCrypto).createKeyMaterialSource(rendezvousKey,
					transportId);
			will(returnValue(keyMaterialSource));
			oneOf(plugin).createRendezvousEndpoint(with(keyMaterialSource),
					with(alice), with(any(ConnectionHandler.class)));
			will(returnValue(rendezvousEndpoint));
		}});
	}

	private void expectGetPlugin() {
		context.checking(new Expectations() {{
			oneOf(pluginManager).getPlugin(transportId);
			will(returnValue(plugin));
			oneOf(plugin).supportsRendezvous();
			will(returnValue(true));
			allowing(plugin).getId();
			will(returnValue(transportId));
		}});
	}

	private AtomicReference<Runnable> expectStartupWithPendingContact(long now)
			throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			// Load the pending contacts
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(db).getPendingContacts(txn);
			will(returnValue(singletonList(pendingContact)));
			// The pending contact has not expired
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(eventBus).broadcast(with(new PredicateMatcher<>(
					PendingContactStateChangedEvent.class, e ->
					e.getPendingContactState() == OFFLINE)));
		}});

		expectDeriveRendezvousKey();
		return expectSchedulePolling();
	}

	private void expectPendingContactExpires(long now) {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
		}});

		expectStateChangedEvent(FAILED);
	}

	private void expectStateChangedEvent(PendingContactState state) {
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(new PredicateMatcher<>(
					PendingContactStateChangedEvent.class, e ->
					e.getPendingContactState() == state)));
		}});
	}

	private void expectCloseEndpoint() throws Exception {
		context.checking(new Expectations() {{
			oneOf(rendezvousEndpoint).close();
		}});
	}
}
