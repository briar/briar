package org.briarproject.lifecycle;

import org.briarproject.api.lifecycle.ShutdownManager;

import org.junit.Test;

public class WindowsShutdownManagerImplTest extends ShutdownManagerImplTest {

	@Override
	protected ShutdownManager createShutdownManager() {
		return new WindowsShutdownManagerImpl();
	}

	@Test
	public void testManagerWaitsForHooksToRun() {
		WindowsShutdownManagerImpl s = new WindowsShutdownManagerImpl();
		SlowHook[] hooks = new SlowHook[10];
		for(int i = 0; i < hooks.length; i++) {
			hooks[i] = new SlowHook();
			s.addShutdownHook(hooks[i]);
		}
		s.runShutdownHooks();
		for(int i = 0; i < hooks.length; i++) assertTrue(hooks[i].finished);
	}

	private static class SlowHook implements Runnable {

		private volatile boolean finished = false;

		public void run() {
			try {
				Thread.sleep(100);
				finished = true;
			} catch(InterruptedException e) {
				fail();
			}
		}
	}
}
