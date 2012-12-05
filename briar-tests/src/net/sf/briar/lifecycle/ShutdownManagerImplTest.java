package net.sf.briar.lifecycle;

import java.util.HashSet;
import java.util.Set;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.lifecycle.ShutdownManager;

import org.junit.Test;

public class ShutdownManagerImplTest extends BriarTestCase {

	@Test
	public void testAddAndRemove() {
		ShutdownManager s = createShutdownManager();
		Set<Integer> handles = new HashSet<Integer>();
		for(int i = 0; i < 100; i++) {
			int handle = s.addShutdownHook(new Runnable() {
				public void run() {}
			});
			// The handles should all be distinct
			assertTrue(handles.add(handle));
		}
		// The hooks should be removable
		for(int handle : handles) assertTrue(s.removeShutdownHook(handle));
		// The hooks should no longer be removable
		for(int handle : handles) assertFalse(s.removeShutdownHook(handle));
	}

	protected ShutdownManager createShutdownManager() {
		return new ShutdownManagerImpl();
	}
}
