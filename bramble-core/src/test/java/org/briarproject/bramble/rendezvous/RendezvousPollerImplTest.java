package org.briarproject.bramble.rendezvous;

import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.rendezvous.RendezvousCrypto;
import org.briarproject.bramble.api.rendezvous.event.RendezvousFailedEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.api.rendezvous.RendezvousConstants.POLLING_INTERVAL_MS;
import static org.briarproject.bramble.api.rendezvous.RendezvousConstants.RENDEZVOUS_TIMEOUT_MS;
import static org.briarproject.bramble.test.TestUtils.getAgreementPrivateKey;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getPendingContact;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;

public class RendezvousPollerImplTest extends BrambleMockTestCase {

	private final ScheduledExecutorService scheduler =
			context.mock(ScheduledExecutorService.class);
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

	private final Executor ioExecutor = new ImmediateExecutor();

	private RendezvousPollerImpl rendezvousPoller;

	@Before
	public void setUp() {
		rendezvousPoller = new RendezvousPollerImpl(ioExecutor, scheduler, db,
				identityManager, transportCrypto, rendezvousCrypto,
				pluginManager, connectionManager, eventBus, clock);
	}

	@Test
	public void testAddsPendingContactsAndSchedulesPollAtStartup()
			throws Exception {
		Transaction txn = new Transaction(null, true);
		PendingContact pendingContact = getPendingContact();
		long now = pendingContact.getTimestamp() + RENDEZVOUS_TIMEOUT_MS - 1000;
		AtomicReference<Runnable> captureExpiryTask = new AtomicReference<>();
		KeyPair handshakeKeyPair =
				new KeyPair(getAgreementPublicKey(), getAgreementPrivateKey());
		SecretKey staticMasterKey = getSecretKey();
		SecretKey rendezvousKey = getSecretKey();

		context.checking(new DbExpectations() {{
			// Load the pending contacts
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(db).getPendingContacts(txn);
			will(returnValue(singletonList(pendingContact)));
			// Schedule the first poll
			oneOf(scheduler).scheduleAtFixedRate(with(any(Runnable.class)),
					with(POLLING_INTERVAL_MS), with(POLLING_INTERVAL_MS),
					with(MILLISECONDS));
			// Calculate the pending contact's expiry time, 1 second from now
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(scheduler).schedule(with(any(Runnable.class)), with(1000L),
					with(MILLISECONDS));
			// Capture the expiry task, we'll run it later
			will(new CaptureArgumentAction<>(captureExpiryTask,
					Runnable.class, 0));
			// Load our handshake key pair
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			will(returnValue(handshakeKeyPair));
			// Derive the rendezvous key
			oneOf(transportCrypto).deriveStaticMasterKey(
					pendingContact.getPublicKey(), handshakeKeyPair);
			will(returnValue(staticMasterKey));
			oneOf(rendezvousCrypto).deriveRendezvousKey(staticMasterKey);
			will(returnValue(rendezvousKey));
		}});

		rendezvousPoller.startService();
		context.assertIsSatisfied();

		context.checking(new Expectations() {{
			// Run the expiry task
			oneOf(eventBus).broadcast(with(any(RendezvousFailedEvent.class)));
		}});

		captureExpiryTask.get().run();
	}
}
