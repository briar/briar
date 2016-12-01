package org.briarproject.bramble.lifecycle;

import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WindowsShutdownManagerImplTest extends ShutdownManagerImplTest {

	@Override
	protected ShutdownManager createShutdownManager() {
		return new WindowsShutdownManagerImpl();
	}

	@Test
	public void testManagerWaitsForHooksToRun() {
		WindowsShutdownManagerImpl s = new WindowsShutdownManagerImpl();
		SlowHook[] hooks = new SlowHook[10];
		for (int i = 0; i < hooks.length; i++) {
			hooks[i] = new SlowHook();
			s.addShutdownHook(hooks[i]);
		}
		s.runShutdownHooks();
		for (SlowHook hook : hooks) assertTrue(hook.finished);
	}

	private static class SlowHook implements Runnable {

		private volatile boolean finished = false;

		@Override
		public void run() {
			try {
				Thread.sleep(100);
				finished = true;
			} catch (InterruptedException e) {
				fail();
			}
		}
	}
}
