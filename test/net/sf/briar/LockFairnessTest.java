package net.sf.briar;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import junit.framework.TestCase;

import org.junit.Test;

public class LockFairnessTest extends TestCase {

	@Test
	public void testWritersDoNotStarevWithFairLocks() throws Exception {
		// Create a fair fair read-write lock
		ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
		// Create a ton of reader threads that repeatedly acquire the read lock,
		// sleep for a few ms and release the lock
		Thread[] readers = new Thread[20];
		for(int i = 0; i < readers.length; i++) {
			readers[i] = new ReaderThread(lock);
		}
		// Stagger the start times of the readers so they release the read lock
		// at different times
		for(int i = 0; i < readers.length; i++) {
			readers[i].start();
			Thread.sleep(7);
		}
		// Create a writer thread, which should be able to acquire the lock
		WriterThread writer = new WriterThread(lock);
		writer.start();
		Thread.sleep(1000);
		// The writer should have acquired the lock
		assertTrue(writer.acquiredLock);
		// Stop the readers
		for(int i = 0; i < readers.length; i++) readers[i].interrupt();
	}

	private static class ReaderThread extends Thread {

		private final ReentrantReadWriteLock lock;

		private ReaderThread(ReentrantReadWriteLock lock) {
			this.lock = lock;
		}

		@Override
		public void run() {
			try {
				while(true) {
					lock.readLock().lock();
					try {
						Thread.sleep(13);
					} finally {
						lock.readLock().unlock();
					}
				}
			} catch(InterruptedException quit) {
				return;
			}
		}
	}

	private static class WriterThread extends Thread {

		private final ReentrantReadWriteLock lock;
		private volatile boolean acquiredLock = false;

		private WriterThread(ReentrantReadWriteLock lock) {
			this.lock = lock;
		}

		@Override
		public void run() {
			lock.writeLock().lock();
			try {
				acquiredLock = true;
			} finally {
				lock.writeLock().unlock();
			}
		}
	}
}
