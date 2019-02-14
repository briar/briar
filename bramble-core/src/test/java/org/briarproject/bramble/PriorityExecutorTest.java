package org.briarproject.bramble;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

import static java.util.Arrays.asList;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PriorityExecutorTest extends BrambleTestCase {

	@Test
	public void testHighPriorityTasksAreDelegatedInOrderOfSubmission()
			throws Exception {
		Executor delegate = newSingleThreadExecutor();
		PriorityExecutor priority = new PriorityExecutor(delegate);
		Executor high = priority.getHighPriorityExecutor();
		testTasksAreDelegatedInOrderOfSubmission(high);
	}

	@Test
	public void testLowPriorityTasksAreDelegatedInOrderOfSubmission()
			throws Exception {
		Executor delegate = newSingleThreadExecutor();
		PriorityExecutor priority = new PriorityExecutor(delegate);
		Executor low = priority.getLowPriorityExecutor();
		testTasksAreDelegatedInOrderOfSubmission(low);
	}

	@Test
	public void testHighPriorityTasksAreRunFirst() throws Exception {
		Executor delegate = newSingleThreadExecutor();
		PriorityExecutor priority = new PriorityExecutor(delegate);
		Executor high = priority.getHighPriorityExecutor();
		Executor low = priority.getLowPriorityExecutor();
		// Submit a task that will block, causing other tasks to be queued
		CountDownLatch cork = new CountDownLatch(1);
		low.execute(() -> {
			try {
				cork.await();
			} catch (InterruptedException e) {
				fail();
			}
		});
		// Submit alternating tasks to the high and low priority executors
		List<Integer> results = new Vector<>();
		CountDownLatch tasksFinished = new CountDownLatch(10);
		for (int i = 0; i < 10; i++) {
			int result = i;
			Runnable task = () -> {
				results.add(result);
				tasksFinished.countDown();
			};
			if (i % 2 == 0) high.execute(task);
			else low.execute(task);
		}
		// Release the cork and wait for all tasks to finish
		cork.countDown();
		tasksFinished.await();
		// The high-priority tasks should have run before the low-priority tasks
		assertEquals(asList(0, 2, 4, 6, 8, 1, 3, 5, 7, 9), results);
	}

	private void testTasksAreDelegatedInOrderOfSubmission(Executor e)
			throws Exception {
		List<Integer> results = new Vector<>();
		CountDownLatch tasksFinished = new CountDownLatch(10);
		for (int i = 0; i < 10; i++) {
			int result = i;
			e.execute(() -> {
				results.add(result);
				tasksFinished.countDown();
			});
		}
		// Wait for all the tasks to finish
		tasksFinished.await();
		// The tasks should have run in the order they were submitted
		assertEquals(asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), results);
	}
}
