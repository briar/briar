package org.briarproject.bramble.system;

import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.OsUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.security.Provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LinuxSecureRandomProviderTest extends BrambleTestCase {

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testGetProviderWritesToRandomDeviceOnFirstCall()
			throws Exception {
		if (!(OsUtils.isLinux())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		// Redirect the provider's output to a file
		File urandom = new File(testDir, "urandom");
		urandom.delete();
		assertTrue(urandom.createNewFile());
		assertEquals(0, urandom.length());
		LinuxSecureRandomProvider p = new LinuxSecureRandomProvider(urandom);
		// Getting a provider should write entropy to the file
		Provider provider = p.getProvider();
		assertNotNull(provider);
		assertEquals("LinuxPRNG", provider.getName());
		// There should be at least 16 bytes from the clock, 8 from the runtime
		long length = urandom.length();
		assertTrue(length >= 24);
		// Getting another provider should not write to the file again
		provider = p.getProvider();
		assertNotNull(provider);
		assertEquals("LinuxPRNG", provider.getName());
		assertEquals(length, urandom.length());
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
