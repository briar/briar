package org.briarproject.bramble.system;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.security.Provider;

import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.util.OsUtils.isLinux;
import static org.briarproject.bramble.util.OsUtils.isMac;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class UnixSecureRandomProviderTest extends BrambleTestCase {

	private final File testDir = getTestDirectory();

	@Before
	public void setUp() {
		assumeTrue(isLinux() || isMac());
		assertTrue(testDir.mkdirs());
	}

	@Test
	public void testGetProviderWritesToRandomDeviceOnFirstCall()
			throws Exception {
		// Redirect the provider's output to a file
		File urandom = new File(testDir, "urandom");
		if (urandom.exists()) assertTrue(urandom.delete());
		assertTrue(urandom.createNewFile());
		assertEquals(0, urandom.length());
		UnixSecureRandomProvider p = new UnixSecureRandomProvider(urandom);
		// Getting a provider should write entropy to the file
		Provider provider = p.getProvider();
		assertNotNull(provider);
		assertEquals("UnixPRNG", provider.getName());
		// There should be at least 16 bytes from the clock, 8 from the runtime
		long length = urandom.length();
		assertTrue(length >= 24);
		// Getting another provider should not write to the file again
		provider = p.getProvider();
		assertNotNull(provider);
		assertEquals("UnixPRNG", provider.getName());
		assertEquals(length, urandom.length());
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}
}
