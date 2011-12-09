package net.sf.briar.plugins.file;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.plugins.file.RemovableDriveMonitor.Callback;
import net.sf.briar.util.OsUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UnixRemovableDriveMonitorTest extends BriarTestCase {

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testNonexistentDir() throws Exception {
		if(!OsUtils.isLinux() || OsUtils.isMacLeopardOrNewer()) {
			System.err.println("Warning: Skipping test");
			return;
		}
		File doesNotExist = new File(testDir, "doesNotExist");
		RemovableDriveMonitor monitor = createMonitor(doesNotExist);
		monitor.start(null);
		monitor.stop();
	}

	@Test
	public void testOneCallbackPerFile() throws Exception {
		if(!OsUtils.isLinux() || OsUtils.isMacLeopardOrNewer()) {
			System.err.println("Warning: Skipping test");
			return;
		}
		// Create a callback that will wait for two files before stopping
		final List<File> detected = new ArrayList<File>();
		final CountDownLatch latch = new CountDownLatch(2);
		final Callback callback = new Callback() {
			public void driveInserted(File f) {
				detected.add(f);
				latch.countDown();
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
		assertTrue(latch.await(1, TimeUnit.SECONDS));
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
			@Override
			protected String[] getPathsToWatch() {
				return new String[] { dir.getPath() };
			}
		};
	}
}
