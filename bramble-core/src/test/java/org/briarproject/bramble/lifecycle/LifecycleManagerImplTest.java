package org.briarproject.bramble.lifecycle;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.lifecycle.event.LifecycleEvent;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.TestCase.assertTrue;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.SUCCESS;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.junit.Assert.assertEquals;

public class LifecycleManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final EventBus eventBus = context.mock(EventBus.class);

	private final SecretKey dbKey = getSecretKey();

	private LifecycleManagerImpl lifecycleManager;

	@Before
	public void setUp() {
		lifecycleManager = new LifecycleManagerImpl(db, eventBus);
	}

	@Test
	public void testOpenDatabaseHooksAreCalledAtStartup() throws Exception {
		Transaction txn = new Transaction(null, false);
		AtomicBoolean called = new AtomicBoolean(false);
		OpenDatabaseHook hook = transaction -> called.set(true);

		context.checking(new DbExpectations() {{
			oneOf(db).open(dbKey, lifecycleManager);
			will(returnValue(false));
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(db).removeTemporaryMessages(txn);
			allowing(eventBus).broadcast(with(any(LifecycleEvent.class)));
		}});

		lifecycleManager.registerOpenDatabaseHook(hook);

		assertEquals(SUCCESS, lifecycleManager.startServices(dbKey));
		assertTrue(called.get());
	}
}
