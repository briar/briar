package org.briarproject.bramble.db;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LockFairnessTest extends BrambleTestCase {

	@Test
	public void testReadersCanShareTheLock() throws Exception {
		// Use a fair lock
		final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
		final CountDownLatch firstReaderHasLock = new CountDownLatch(1);
		final CountDownLatch firstReaderHasFinished = new CountDownLatch(1);
		final CountDownLatch secondReaderHasLock = new CountDownLatch(1);
		final CountDownLatch secondReaderHasFinished = new CountDownLatch(1);
		// First reader
		Thread first = new Thread() {
			@Override
			public void run() {
				try {
					// Acquire the lock
					lock.readLock().lock();
					try {
						// Allow the second reader to acquire the lock
						firstReaderHasLock.countDown();
						// Wait for the second reader to acquire the lock
						assertTrue(secondReaderHasLock.await(10, SECONDS));
					} finally {
						// Release the lock
						lock.readLock().unlock();
					}
				} catch (InterruptedException e) {
					fail();
				}
				firstReaderHasFinished.countDown();
			}
		};
		first.start();
		// Second reader
		Thread second = new Thread() {
			@Override
			public void run() {
				try {
					// Wait for the first reader to acquire the lock
					assertTrue(firstReaderHasLock.await(10, SECONDS));
					// Acquire the lock
					lock.readLock().lock();
					try {
						// Allow the first reader to release the lock
						secondReaderHasLock.countDown();
					} finally {
						// Release the lock
						lock.readLock().unlock();
					}
				} catch (InterruptedException e) {
					fail();
				}
				secondReaderHasFinished.countDown();
			}
		};
		second.start();
		// Wait for both readers to finish
		assertTrue(firstReaderHasFinished.await(10, SECONDS));
		assertTrue(secondReaderHasFinished.await(10, SECONDS));
	}

	@Test
	public void testWritersDoNotStarve() throws Exception {
		// Use a fair lock
		final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
		final CountDownLatch firstReaderHasLock = new CountDownLatch(1);
		final CountDownLatch firstReaderHasFinished = new CountDownLatch(1);
		final CountDownLatch secondReaderHasFinished = new CountDownLatch(1);
		final CountDownLatch writerHasFinished = new CountDownLatch(1);
		final AtomicBoolean secondReaderHasHeldLock = new AtomicBoolean(false);
		final AtomicBoolean writerHasHeldLock = new AtomicBoolean(false);
		// First reader
		Thread first = new Thread() {
			@Override
			public void run() {
				try {
					// Acquire the lock
					lock.readLock().lock();
					try {
						// Allow the other threads to acquire the lock
						firstReaderHasLock.countDown();
						// Wait for both other threads to wait for the lock
						while (lock.getQueueLength() < 2) Thread.sleep(10);
						// No other thread should have acquired the lock
						assertFalse(secondReaderHasHeldLock.get());
						assertFalse(writerHasHeldLock.get());
					} finally {
						// Release the lock
						lock.readLock().unlock();
					}
				} catch (InterruptedException e) {
					fail();
				}
				firstReaderHasFinished.countDown();
			}
		};
		first.start();
		// Writer
		Thread writer = new Thread() {
			@Override
			public void run() {
				try {
					// Wait for the first reader to acquire the lock
					assertTrue(firstReaderHasLock.await(10, SECONDS));
					// Acquire the lock
					lock.writeLock().lock();
					try {
						writerHasHeldLock.set(true);
						// The second reader should not overtake the writer
						assertFalse(secondReaderHasHeldLock.get());
					} finally {
						lock.writeLock().unlock();
					}
				} catch (InterruptedException e) {
					fail();
				}
				writerHasFinished.countDown();
			}
		};
		writer.start();
		// Second reader
		Thread second = new Thread() {
			@Override
			public void run() {
				try {
					// Wait for the first reader to acquire the lock
					assertTrue(firstReaderHasLock.await(10, SECONDS));
					// Wait for the writer to wait for the lock
					while (lock.getQueueLength() < 1) Thread.sleep(10);
					// Acquire the lock
					lock.readLock().lock();
					try {
						secondReaderHasHeldLock.set(true);
						// The second reader should not overtake the writer
						assertTrue(writerHasHeldLock.get());
					} finally {
						lock.readLock().unlock();
					}
				} catch (InterruptedException e) {
					fail();
				}
				secondReaderHasFinished.countDown();
			}
		};
		second.start();
		// Wait for all the threads to finish
		assertTrue(firstReaderHasFinished.await(10, SECONDS));
		assertTrue(secondReaderHasFinished.await(10, SECONDS));
		assertTrue(writerHasFinished.await(10, SECONDS));
	}
}
