package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class PollingRemovableDriveMonitorTest extends TestCase {

	@Test
	public void testOneCallbackPerFile() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		final List<File> detected = new ArrayList<File>();
		final File file1 = new File("foo");
		final File file2 = new File("bar");
		final List<File> noDrives = Collections.emptyList();
		final List<File> twoDrives = new ArrayList<File>();
		twoDrives.add(file1);
		twoDrives.add(file2);
		// Create a finder that returns no files the first time, then two files
		Mockery context = new Mockery();
		final RemovableDriveFinder finder =
			context.mock(RemovableDriveFinder.class);
		context.checking(new Expectations() {{
			oneOf(finder).findRemovableDrives();
			will(returnValue(noDrives));
			oneOf(finder).findRemovableDrives();
			will(returnValue(twoDrives));
		}});
		// Create a monitor that will wait for two files before stopping
		final RemovableDriveMonitor monitor =
			new PollingRemovableDriveMonitor(finder, 10);
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
		// Wait for the monitor to detect the files
		assertTrue(latch.await(2, TimeUnit.SECONDS));
		monitor.stop();
		// Check that both files were detected
		assertEquals(2, detected.size());
		assertTrue(detected.contains(file1));
		assertTrue(detected.contains(file2));
		// Check that the finder was polled twice
		context.assertIsSatisfied();
	}

	@Test
	public void testExceptionRethrownWhenWaiting() throws Exception {
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
		// The monitor should rethrow the exception when it waits
		final RemovableDriveMonitor monitor =
			new PollingRemovableDriveMonitor(finder, 10);
		monitor.start();
		try {
			monitor.waitForInsertion();
			fail();
		} catch(IOException expected) {}
		// The exception shouldn't be thrown again
		monitor.stop();
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
		// The monitor should rethrow the exception when it stops
		final RemovableDriveMonitor monitor =
			new PollingRemovableDriveMonitor(finder, 10);
		monitor.start();
		Thread.sleep(50);
		try {
			monitor.stop();
			fail();
		} catch(IOException expected) {}
		context.assertIsSatisfied();
	}
}
