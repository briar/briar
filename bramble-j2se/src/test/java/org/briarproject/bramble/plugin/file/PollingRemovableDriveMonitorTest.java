package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.plugin.file.RemovableDriveMonitor.Callback;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PollingRemovableDriveMonitorTest extends BrambleTestCase {

	@Test
	public void testOneCallbackPerFile() throws Exception {
		// Create a finder that returns no files the first time, then two files
		final File file1 = new File("foo");
		final File file2 = new File("bar");
		@NotNullByDefault
		final RemovableDriveFinder finder = new RemovableDriveFinder() {

			private AtomicBoolean firstCall = new AtomicBoolean(true);

			@Override
			public Collection<File> findRemovableDrives() throws IOException {
				if (firstCall.getAndSet(false)) return Collections.emptyList();
				else return Arrays.asList(file1, file2);
			}
		};
		// Create a callback that waits for two files
		final CountDownLatch latch = new CountDownLatch(2);
		final List<File> detected = new ArrayList<>();
		@NotNullByDefault
		Callback callback = new Callback() {

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
		final RemovableDriveMonitor monitor = new PollingRemovableDriveMonitor(
				Executors.newCachedThreadPool(), finder, 1);
		monitor.start(callback);
		// Wait for the monitor to detect the files
		assertTrue(latch.await(10, SECONDS));
		monitor.stop();
		// Check that both files were detected
		assertEquals(2, detected.size());
		assertTrue(detected.contains(file1));
		assertTrue(detected.contains(file2));
	}

	@Test
	public void testExceptionCallback() throws Exception {
		// Create a finder that throws an exception the second time it's polled
		final RemovableDriveFinder finder = new RemovableDriveFinder() {

			private AtomicBoolean firstCall = new AtomicBoolean(true);

			@Override
			public Collection<File> findRemovableDrives() throws IOException {
				if (firstCall.getAndSet(false)) return Collections.emptyList();
				else throw new IOException();
			}
		};
		// Create a callback that waits for an exception
		final CountDownLatch latch = new CountDownLatch(1);
		@NotNullByDefault
		Callback callback = new Callback() {

			@Override
			public void driveInserted(File root) {
				fail();
			}

			@Override
			public void exceptionThrown(IOException e) {
				latch.countDown();
			}
		};
		// Create the monitor and start it
		final RemovableDriveMonitor monitor = new PollingRemovableDriveMonitor(
				Executors.newCachedThreadPool(), finder, 1);
		monitor.start(callback);
		assertTrue(latch.await(10, SECONDS));
		monitor.stop();
	}
}
