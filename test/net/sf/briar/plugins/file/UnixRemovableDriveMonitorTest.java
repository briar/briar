package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UnixRemovableDriveMonitorTest extends TestCase {

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testNonexistentDir() throws Exception {
		File doesNotExist = new File(testDir, "doesNotExist");
		RemovableDriveMonitor monitor = createMonitor(doesNotExist);
		monitor.start();
		monitor.stop();
	}

	@Test
	public void testOneCallbackPerFile() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		final List<File> detected = new ArrayList<File>();
		// Create a monitor that will wait for two files before stopping
		final RemovableDriveMonitor monitor = createMonitor(testDir);
		monitor.start();
		new Thread() {
			@Override
			public void run() {
				try {
					detected.add(monitor.waitForInsertion());
					detected.add(monitor.waitForInsertion());
					latch.countDown();
				} catch(IOException e) {
					fail();
				}
			}
		}.start();
		// Create two files in the test directory
		File file1 = new File(testDir, "1");
		File file2 = new File(testDir, "2");
		assertTrue(file1.createNewFile());
		assertTrue(file2.createNewFile());
		// Wait for the monitor to detect the files
		assertTrue(latch.await(2, TimeUnit.SECONDS));
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
