package org.briarproject.system;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.Bytes;
import org.briarproject.util.OsUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

import static org.briarproject.api.system.SeedProvider.SEED_BYTES;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LinuxSeedProviderTest extends BriarTestCase {

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testSeedAppearsSane() {
		if (!(OsUtils.isLinux())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		Set<Bytes> seeds = new HashSet<Bytes>();
		LinuxSeedProvider p = new LinuxSeedProvider();
		for (int i = 0; i < 1000; i++) {
			byte[] seed = p.getSeed();
			assertEquals(SEED_BYTES, seed.length);
			assertTrue(seeds.add(new Bytes(seed)));
		}
	}

	@Test
	public void testEntropyIsWrittenToPool() throws Exception {
		if (!(OsUtils.isLinux())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		// Redirect the provider's entropy to a file
		File urandom = new File(testDir, "urandom");
		urandom.delete();
		assertTrue(urandom.createNewFile());
		assertEquals(0, urandom.length());
		String path = urandom.getAbsolutePath();
		LinuxSeedProvider p = new LinuxSeedProvider(path, "/dev/urandom");
		p.getSeed();
		// There should be 16 bytes from the clock, plus network interfaces
		assertTrue(urandom.length() > 20);
	}

	@Test
	public void testSeedIsReadFromPool() throws Exception {
		if (!(OsUtils.isLinux())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		// Generate a seed
		byte[] seed = TestUtils.getRandomBytes(SEED_BYTES);
		// Write the seed to a file
		File urandom = new File(testDir, "urandom");
		urandom.delete();
		FileOutputStream out = new FileOutputStream(urandom);
		out.write(seed);
		out.flush();
		out.close();
		assertTrue(urandom.exists());
		assertEquals(SEED_BYTES, urandom.length());
		// Check that the provider reads the seed from the file
		String path = urandom.getAbsolutePath();
		LinuxSeedProvider p = new LinuxSeedProvider("/dev/urandom", path);
		assertArrayEquals(seed, p.getSeed());
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
