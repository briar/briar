package net.sf.briar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Test;

public class LockFairnessTest extends TestCase {

	private final ReentrantReadWriteLock lock =
		new ReentrantReadWriteLock(true); // Fair
	private final List<Thread> finished = new ArrayList<Thread>();

	@Test
	public void testReadersCanShareTheLock() throws Exception {
		// Create a long-running reader and a short-running reader
		Thread longReader = new ReaderThread(lock, 100);
		Thread shortReader = new ReaderThread(lock, 1);
		// The short-running reader should complete before the long-running one
		longReader.start();
		Thread.sleep(1);
		shortReader.start();
		// Wait for the long-running reader to finish (it should finish last)
		longReader.join();
		// The short-running reader should have finished first
		assertEquals(2, finished.size());
		assertEquals(shortReader, finished.get(0));
		assertEquals(longReader, finished.get(1));
	}

	@Test
	public void testWritersDoNotStarve() throws Exception {
		// Create a long-running reader and a short-running reader
		Thread longReader = new ReaderThread(lock, 100);
		Thread shortReader = new ReaderThread(lock, 1);
		// Create a long-running writer
		Thread writer = new WriterThread(lock, 100);
		// The short-running reader should not overtake the writer and share
		// the lock with the long-running reader
		longReader.start();
		Thread.sleep(1);
		writer.start();
		Thread.sleep(1);
		shortReader.start();
		// Wait for the short-running reader to finish (it should finish last)
		shortReader.join();
		// The short-running reader should have finished last
		assertEquals(3, finished.size());
		assertEquals(longReader, finished.get(0));
		assertEquals(writer, finished.get(1));
		assertEquals(shortReader, finished.get(2));
	}

	@After
	public void tearDown() {
		finished.clear();
	}

	private class ReaderThread extends Thread {

		private final ReentrantReadWriteLock lock;
		private final int sleepTime;

		private ReaderThread(ReentrantReadWriteLock lock, int sleepTime) {
			this.lock = lock;
			this.sleepTime = sleepTime;
		}

		@Override
		public void run() {
			lock.readLock().lock();
			try {
				Thread.sleep(sleepTime);
				finished.add(this);
			} catch(InterruptedException e) {
				e.printStackTrace();
			} finally {
				lock.readLock().unlock();
			}
		}
	}

	private class WriterThread extends Thread {

		private final ReentrantReadWriteLock lock;
		private final int sleepTime;

		private WriterThread(ReentrantReadWriteLock lock, int sleepTime) {
			this.lock = lock;
			this.sleepTime = sleepTime;
		}

		@Override
		public void run() {
			lock.writeLock().lock();
			try {
				Thread.sleep(sleepTime);
				finished.add(this);
			} catch(InterruptedException e) {
				e.printStackTrace();
			} finally {
				lock.writeLock().unlock();
			}
		}
	}
}
