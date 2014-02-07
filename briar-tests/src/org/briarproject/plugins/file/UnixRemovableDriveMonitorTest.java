package org.briarproject.plugins.file;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.plugins.file.RemovableDriveMonitor.Callback;
import org.briarproject.util.OsUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UnixRemovableDriveMonitorTest extends BriarTestCase {

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() {
		assumeTrue(OsUtils.isLinux() || OsUtils.isMacLeopardOrNewer());
		testDir.mkdirs();
	}

	@Test
	public void testNonexistentDir() throws Exception {
		File doesNotExist = new File(testDir, "doesNotExist");
		RemovableDriveMonitor monitor = createMonitor(doesNotExist);
		monitor.start(new Callback() {

			public void driveInserted(File root) {
				fail();
			}

			public void exceptionThrown(IOException e) {
				fail();
			}
		});
		monitor.stop();
	}

	@Test
	public void testOneCallbackPerFile() throws Exception {
		// Create a callback that will wait for two files before stopping
		final List<File> detected = new ArrayList<File>();
		final CountDownLatch latch = new CountDownLatch(2);
		final Callback callback = new Callback() {

			public void driveInserted(File f) {
				detected.add(f);
				latch.countDown();
			}

			public void exceptionThrown(IOException e) {
				fail();
			}
		};
		// Create the monitor and start it
		RemovableDriveMonitor monitor = createMonitor(testDir);
		monitor.start(callback);
		// Create two files in the test directory
		File file1 = new File(testDir, "1");
		File file2 = new File(testDir, "2");
		assertTrue(file1.createNewFile());
		assertTrue(file2.createNewFile());
		// Wait for the monitor to detect the files
		assertTrue(latch.await(5, SECONDS));
		monitor.stop();
		// Check that both files were detected
		assertEquals(2, detected.size());
		assertTrue(detected.contains(file1));
		assertTrue(detected.contains(file2));
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	private RemovableDriveMonitor createMonitor(final File dir) {
		return new UnixRemovableDriveMonitor() {
			protected String[] getPathsToWatch() {
				return new String[] { dir.getPath() };
			}
		};
	}
}
