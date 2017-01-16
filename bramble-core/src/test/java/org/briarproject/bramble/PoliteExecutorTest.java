package org.briarproject.bramble;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PoliteExecutorTest extends BrambleTestCase {

	private static final String TAG = "Test";
	private static final int TASKS = 10;

	@Test
	public void testTasksAreDelegatedInOrderOfSubmission() throws Exception {
		// Delegate to a single-threaded executor
		Executor delegate = Executors.newSingleThreadExecutor();
		// Allow all the tasks to be delegated straight away
		PoliteExecutor polite = new PoliteExecutor(TAG, delegate, TASKS * 2);
		final List<Integer> list = new Vector<Integer>();
		final CountDownLatch latch = new CountDownLatch(TASKS);
		for (int i = 0; i < TASKS; i++) {
			final int result = i;
			polite.execute(new Runnable() {
				@Override
				public void run() {
					list.add(result);
					latch.countDown();
				}
			});
		}
		// Wait for all the tasks to finish
		latch.await();
		// The tasks should have run in the order they were submitted
		assertEquals(ascendingOrder(), list);
	}

	@Test
	public void testQueuedTasksAreDelegatedInOrderOfSubmission()
			throws Exception {
		// Delegate to a single-threaded executor
		Executor delegate = Executors.newSingleThreadExecutor();
		// Allow two tasks to be delegated at a time
		PoliteExecutor polite = new PoliteExecutor(TAG, delegate, 2);
		final List<Integer> list = new Vector<Integer>();
		final CountDownLatch latch = new CountDownLatch(TASKS);
		for (int i = 0; i < TASKS; i++) {
			final int result = i;
			polite.execute(new Runnable() {
				@Override
				public void run() {
					list.add(result);
					latch.countDown();
				}
			});
		}
		// Wait for all the tasks to finish
		latch.await();
		// The tasks should have run in the order they were submitted
		assertEquals(ascendingOrder(), list);
	}

	@Test
	public void testTasksRunInParallelOnDelegate() throws Exception {
		// Delegate to a multi-threaded executor
		Executor delegate = Executors.newCachedThreadPool();
		// Allow all the tasks to be delegated straight away
		PoliteExecutor polite = new PoliteExecutor(TAG, delegate, TASKS * 2);
		final List<Integer> list = new Vector<Integer>();
		final CountDownLatch[] latches = new CountDownLatch[TASKS];
		for (int i = 0; i < TASKS; i++) latches[i] = new CountDownLatch(1);
		for (int i = 0; i < TASKS; i++) {
			final int result = i;
			polite.execute(new Runnable() {
				@Override
				public void run() {
					try {
						// Each task waits for the next task, if any, to finish
						if (result < TASKS - 1) latches[result + 1].await();
						list.add(result);
					} catch (InterruptedException e) {
						fail();
					}
					latches[result].countDown();
				}
			});
		}
		// Wait for all the tasks to finish
		for (int i = 0; i < TASKS; i++) latches[i].await();
		// The tasks should have finished in reverse order
		assertEquals(descendingOrder(), list);
	}

	@Test
	public void testTasksDoNotRunInParallelOnDelegate() throws Exception {
		// Delegate to a multi-threaded executor
		Executor delegate = Executors.newCachedThreadPool();
		// Allow one task to be delegated at a time
		PoliteExecutor polite = new PoliteExecutor(TAG, delegate, 1);
		final List<Integer> list = new Vector<Integer>();
		final CountDownLatch latch = new CountDownLatch(TASKS);
		for (int i = 0; i < TASKS; i++) {
			final int result = i;
			polite.execute(new Runnable() {
				@Override
				public void run() {
					try {
						// Each task runs faster than the previous task
						Thread.sleep(TASKS - result);
						list.add(result);
					} catch (InterruptedException e) {
						fail();
					}
					latch.countDown();
				}
			});
		}
		// Wait for all the tasks to finish
		latch.await();
		// The tasks should have finished in the order they were submitted
		assertEquals(ascendingOrder(), list);
	}

	private List<Integer> ascendingOrder() {
		Integer[] array = new Integer[TASKS];
		for (int i = 0; i < TASKS; i++) array[i] = i;
		return Arrays.asList(array);
	}

	private List<Integer> descendingOrder() {
		Integer[] array = new Integer[TASKS];
		for (int i = 0; i < TASKS; i++) array[i] = TASKS - 1 - i;
		return Arrays.asList(array);
	}
}
