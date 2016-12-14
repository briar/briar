package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.plugin.file.RemovableDriveMonitor.Callback;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.OsUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UnixRemovableDriveMonitorTest extends BrambleTestCase {

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testNonexistentDir() throws Exception {
		if (!(OsUtils.isLinux() || OsUtils.isMacLeopardOrNewer())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		File doesNotExist = new File(testDir, "doesNotExist");
		RemovableDriveMonitor monitor = createMonitor(doesNotExist);
		@NotNullByDefault
		Callback callback = new Callback() {

			@Override
			public void driveInserted(File root) {
				fail();
			}

			@Override
			public void exceptionThrown(IOException e) {
				fail();
			}
		};
		monitor.start(callback);
		monitor.stop();
	}

	@Test
	public void testOneCallbackPerFile() throws Exception {
		if (!(OsUtils.isLinux() || OsUtils.isMacLeopardOrNewer())) {
			System.err.println("WARNING: Skipping test, can't run on this OS");
			return;
		}
		// Create a callback that will wait for two files before stopping
		final List<File> detected = new ArrayList<>();
		final CountDownLatch latch = new CountDownLatch(2);
		@NotNullByDefault
		final Callback callback = new Callback() {

			@Override
			public void driveInserted(File f) {
				detected.add(f);
				latch.countDown();
			}

			@Override
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
		@NotNullByDefault
		RemovableDriveMonitor monitor = new UnixRemovableDriveMonitor() {
			@Override
			protected String[] getPathsToWatch() {
				return new String[] {dir.getPath()};
			}
		};
		return monitor;
	}
}
