package org.briarproject.bramble.system;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.IoUtils;
import org.briarproject.bramble.util.OsUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class LinuxSecureRandomSpiTest extends BrambleTestCase {

	private static final File RANDOM_DEVICE = new File("/dev/urandom");
	private static final int SEED_BYTES = 32;

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testSeedsAreDistinct() {
		if (!(OsUtils.isLinux())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		Set<Bytes> seeds = new HashSet<Bytes>();
		LinuxSecureRandomSpi engine = new LinuxSecureRandomSpi();
		for (int i = 0; i < 1000; i++) {
			byte[] seed = engine.engineGenerateSeed(SEED_BYTES);
			assertEquals(SEED_BYTES, seed.length);
			assertTrue(seeds.add(new Bytes(seed)));
		}
	}

	@Test
	public void testEngineSetSeedWritesToRandomDevice() throws Exception {
		if (!(OsUtils.isLinux())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		// Redirect the engine's output to a file
		File urandom = new File(testDir, "urandom");
		urandom.delete();
		assertTrue(urandom.createNewFile());
		assertEquals(0, urandom.length());
		// Generate a seed
		byte[] seed = TestUtils.getRandomBytes(SEED_BYTES);
		// Check that the engine writes the seed to the file
		LinuxSecureRandomSpi engine = new LinuxSecureRandomSpi(RANDOM_DEVICE,
				urandom);
		engine.engineSetSeed(seed);
		assertEquals(SEED_BYTES, urandom.length());
		byte[] written = new byte[SEED_BYTES];
		FileInputStream in = new FileInputStream(urandom);
		IoUtils.read(in, written);
		in.close();
		assertArrayEquals(seed, written);
	}

	@Test
	public void testEngineNextBytesReadsFromRandomDevice() throws Exception {
		if (!(OsUtils.isLinux())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		// Generate some entropy
		byte[] entropy = TestUtils.getRandomBytes(SEED_BYTES);
		// Write the entropy to a file
		File urandom = new File(testDir, "urandom");
		urandom.delete();
		FileOutputStream out = new FileOutputStream(urandom);
		out.write(entropy);
		out.flush();
		out.close();
		assertTrue(urandom.exists());
		assertEquals(SEED_BYTES, urandom.length());
		// Check that the engine reads from the file
		LinuxSecureRandomSpi engine = new LinuxSecureRandomSpi(urandom,
				RANDOM_DEVICE);
		byte[] b = new byte[SEED_BYTES];
		engine.engineNextBytes(b);
		assertArrayEquals(entropy, b);
	}

	@Test
	public void testEngineGenerateSeedReadsFromRandomDevice() throws Exception {
		if (!(OsUtils.isLinux())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		// Generate some entropy
		byte[] entropy = TestUtils.getRandomBytes(SEED_BYTES);
		// Write the entropy to a file
		File urandom = new File(testDir, "urandom");
		urandom.delete();
		FileOutputStream out = new FileOutputStream(urandom);
		out.write(entropy);
		out.flush();
		out.close();
		assertTrue(urandom.exists());
		assertEquals(SEED_BYTES, urandom.length());
		// Check that the engine reads from the file
		LinuxSecureRandomSpi engine = new LinuxSecureRandomSpi(urandom,
				RANDOM_DEVICE);
		byte[] b = engine.engineGenerateSeed(SEED_BYTES);
		assertArrayEquals(entropy, b);
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
