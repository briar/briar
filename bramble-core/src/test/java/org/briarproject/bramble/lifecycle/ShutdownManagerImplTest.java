package org.briarproject.bramble.lifecycle;

import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShutdownManagerImplTest extends BrambleTestCase {

	@Test
	public void testAddAndRemove() {
		ShutdownManager s = createShutdownManager();
		Set<Integer> handles = new HashSet<Integer>();
		for (int i = 0; i < 100; i++) {
			int handle = s.addShutdownHook(new Runnable() {
				@Override
				public void run() {
				}
			});
			// The handles should all be distinct
			assertTrue(handles.add(handle));
		}
		// The hooks should be removable
		for (int handle : handles) assertTrue(s.removeShutdownHook(handle));
		// The hooks should no longer be removable
		for (int handle : handles) assertFalse(s.removeShutdownHook(handle));
	}

	protected ShutdownManager createShutdownManager() {
		return new ShutdownManagerImpl();
	}
}
