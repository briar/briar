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

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook.Priority.EARLY;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook.Priority.LATE;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook.Priority.NORMAL;
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
	public void testOpenDatabaseHooksRunInOrderOfPriority() throws Exception {
		Transaction txn = new Transaction(null, false);
		List<Integer> results = new ArrayList<>();
		OpenDatabaseHook hook1 = transaction -> results.add(1);
		OpenDatabaseHook hook2 = transaction -> results.add(2);
		OpenDatabaseHook hook3 = transaction -> results.add(3);

		context.checking(new DbExpectations() {{
			oneOf(db).open(dbKey, lifecycleManager);
			will(returnValue(false));
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			allowing(eventBus).broadcast(with(any(LifecycleEvent.class)));
		}});

		lifecycleManager.registerOpenDatabaseHook(hook1, LATE);
		lifecycleManager.registerOpenDatabaseHook(hook2, NORMAL);
		lifecycleManager.registerOpenDatabaseHook(hook3, EARLY);

		assertEquals(SUCCESS, lifecycleManager.startServices(dbKey));
		assertEquals(asList(3, 2, 1), results);
	}
}
