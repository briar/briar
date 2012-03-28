package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.sf.briar.BriarTestCase;
import net.sf.briar.plugins.file.RemovableDriveMonitor.Callback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class PollingRemovableDriveMonitorTest extends BriarTestCase {

	@Test
	public void testOneCallbackPerFile() throws Exception {
		final File file1 = new File("foo");
		final File file2 = new File("bar");
		// Create a finder that returns no files the first time, then two files
		final List<File> noDrives = Collections.emptyList();
		final List<File> twoDrives = new ArrayList<File>();
		twoDrives.add(file1);
		twoDrives.add(file2);
		Mockery context = new Mockery();
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		context.checking(new Expectations() {{
			oneOf(finder).findRemovableDrives();
			will(returnValue(noDrives));
			oneOf(finder).findRemovableDrives();
			will(returnValue(twoDrives));
		}});
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
		final RemovableDriveMonitor monitor = new PollingRemovableDriveMonitor(
				Executors.newCachedThreadPool(), finder, 10);
		monitor.start(callback);
		// Wait for the monitor to detect the files
		assertTrue(latch.await(1, TimeUnit.SECONDS));
		monitor.stop();
		// Check that both files were detected
		assertEquals(2, detected.size());
		assertTrue(detected.contains(file1));
		assertTrue(detected.contains(file2));
		// Check that the finder was polled twice
		context.assertIsSatisfied();
	}

	@Test
	public void testExceptionRethrownWhenStopping() throws Exception {
		final List<File> noDrives = Collections.emptyList();
		// Create a finder that throws an exception the second time it's polled
		Mockery context = new Mockery();
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		context.checking(new Expectations() {{
			oneOf(finder).findRemovableDrives();
			will(returnValue(noDrives));
			oneOf(finder).findRemovableDrives();
			will(throwException(new IOException()));
		}});
		// Create the monitor, start it, and give it some time to run
		final RemovableDriveMonitor monitor = new PollingRemovableDriveMonitor(
				Executors.newCachedThreadPool(), finder, 10);
		monitor.start(new Callback() {
			public void driveInserted(File root) {
				fail();
			}
		});
		Thread.sleep(100);
		// The monitor should rethrow the exception when it stops
		try {
			monitor.stop();
			fail();
		} catch(IOException expected) {}
		context.assertIsSatisfied();
	}
}
