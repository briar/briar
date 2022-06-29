package org.briarproject.bramble.lifecycle;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.event.LifecycleEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STARTING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STOPPED;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.ALREADY_RUNNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.CLOCK_ERROR;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.SUCCESS;
import static org.briarproject.bramble.api.system.Clock.MAX_REASONABLE_TIME_MS;
import static org.briarproject.bramble.api.system.Clock.MIN_REASONABLE_TIME_MS;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.junit.Assert.assertEquals;

public class LifecycleManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final EventBus eventBus = context.mock(EventBus.class);
	private final Clock clock = context.mock(Clock.class);
	private final OpenDatabaseHook hook = context.mock(OpenDatabaseHook.class);
	private final Service service = context.mock(Service.class);

	private final SecretKey dbKey = getSecretKey();

	private final LifecycleManagerImpl lifecycleManager =
			new LifecycleManagerImpl(db, eventBus, clock);

	@Test
	public void testOpenDatabaseHooksAreCalledAtStartup() throws Exception {
		long now = System.currentTimeMillis();
		Transaction txn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(db).open(dbKey, lifecycleManager);
			will(returnValue(false));
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(db).removeTemporaryMessages(txn);
			oneOf(hook).onDatabaseOpened(txn);
			allowing(eventBus).broadcast(with(any(LifecycleEvent.class)));
		}});

		lifecycleManager.registerOpenDatabaseHook(hook);

		assertEquals(SUCCESS, lifecycleManager.startServices(dbKey));
		assertEquals(RUNNING, lifecycleManager.getLifecycleState());
	}

	@Test
	public void testServicesAreStartedAndStopped() throws Exception {
		long now = System.currentTimeMillis();
		Transaction txn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(db).open(dbKey, lifecycleManager);
			will(returnValue(false));
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(db).removeTemporaryMessages(txn);
			oneOf(service).startService();
			allowing(eventBus).broadcast(with(any(LifecycleEvent.class)));
		}});

		lifecycleManager.registerService(service);

		assertEquals(SUCCESS, lifecycleManager.startServices(dbKey));
		assertEquals(RUNNING, lifecycleManager.getLifecycleState());
		context.assertIsSatisfied();

		context.checking(new Expectations() {{
			oneOf(db).close();
			oneOf(service).stopService();
			allowing(eventBus).broadcast(with(any(LifecycleEvent.class)));
		}});

		lifecycleManager.stopServices();
		assertEquals(STOPPED, lifecycleManager.getLifecycleState());
	}

	@Test
	public void testStartupFailsIfClockIsUnreasonablyBehind() {

		context.checking(new DbExpectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(MIN_REASONABLE_TIME_MS - 1));
		}});

		assertEquals(CLOCK_ERROR, lifecycleManager.startServices(dbKey));
		assertEquals(STARTING, lifecycleManager.getLifecycleState());
	}

	@Test
	public void testStartupFailsIfClockIsUnreasonablyAhead() {

		context.checking(new DbExpectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(MAX_REASONABLE_TIME_MS + 1));
		}});

		assertEquals(CLOCK_ERROR, lifecycleManager.startServices(dbKey));
		assertEquals(STARTING, lifecycleManager.getLifecycleState());
	}

	@Test
	public void testSecondCallToStartServicesReturnsEarly() throws Exception {
		long now = System.currentTimeMillis();
		Transaction txn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(db).open(dbKey, lifecycleManager);
			will(returnValue(false));
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(db).removeTemporaryMessages(txn);
			allowing(eventBus).broadcast(with(any(LifecycleEvent.class)));
		}});

		assertEquals(SUCCESS, lifecycleManager.startServices(dbKey));
		assertEquals(RUNNING, lifecycleManager.getLifecycleState());
		context.assertIsSatisfied();

		// Calling startServices() again should not try to open the DB or
		// start the services again
		assertEquals(ALREADY_RUNNING, lifecycleManager.startServices(dbKey));
		assertEquals(RUNNING, lifecycleManager.getLifecycleState());
	}

	@Test
	public void testSecondCallToStopServicesReturnsEarly() throws Exception {
		long now = System.currentTimeMillis();
		Transaction txn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(db).open(dbKey, lifecycleManager);
			will(returnValue(false));
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(db).removeTemporaryMessages(txn);
			allowing(eventBus).broadcast(with(any(LifecycleEvent.class)));
		}});

		assertEquals(SUCCESS, lifecycleManager.startServices(dbKey));
		assertEquals(RUNNING, lifecycleManager.getLifecycleState());
		context.assertIsSatisfied();

		context.checking(new Expectations() {{
			oneOf(db).close();
			allowing(eventBus).broadcast(with(any(LifecycleEvent.class)));
		}});

		lifecycleManager.stopServices();
		assertEquals(STOPPED, lifecycleManager.getLifecycleState());
		context.assertIsSatisfied();

		// Calling stopServices() again should not broadcast another event or
		// try to close the DB again
		lifecycleManager.stopServices();
		assertEquals(STOPPED, lifecycleManager.getLifecycleState());
	}
}
