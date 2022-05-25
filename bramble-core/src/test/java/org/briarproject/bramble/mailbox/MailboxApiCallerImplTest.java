package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.Supplier;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.jmock.Expectations;
import org.jmock.lib.action.DoAllAction;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.mailbox.MailboxApiCaller.MAX_RETRY_INTERVAL_MS;
import static org.briarproject.bramble.mailbox.MailboxApiCaller.MIN_RETRY_INTERVAL_MS;

public class MailboxApiCallerImplTest extends BrambleMockTestCase {

	private final TaskScheduler taskScheduler =
			context.mock(TaskScheduler.class);
	private final Executor ioExecutor = context.mock(Executor.class);
	private final BooleanSupplier supplier =
			context.mock(BooleanSupplier.class);
	private final Cancellable scheduledTask = context.mock(Cancellable.class);

	private final MailboxApiCallerImpl caller =
			new MailboxApiCallerImpl(taskScheduler, ioExecutor);

	@Test
	public void testSubmitsTaskImmediately() {
		// Calling retryWithBackoff() should schedule the first try immediately
		AtomicReference<Runnable> runnable = new AtomicReference<>(null);
		context.checking(new Expectations() {{
			oneOf(ioExecutor).execute(with(any(Runnable.class)));
			will(new CaptureArgumentAction<>(runnable, Runnable.class, 0));
		}});

		caller.retryWithBackoff(supplier);

		// When the task runs, the supplier should be called. The supplier
		// returns false, so no retries should be scheduled
		context.checking(new Expectations() {{
			oneOf(supplier).get();
			will(returnValue(false));
		}});

		runnable.get().run();
	}

	@Test
	public void testDoesNotRetryTaskIfCancelled() {
		// Calling retryWithBackoff() should schedule the first try immediately
		AtomicReference<Runnable> runnable = new AtomicReference<>(null);
		context.checking(new Expectations() {{
			oneOf(ioExecutor).execute(with(any(Runnable.class)));
			will(new CaptureArgumentAction<>(runnable, Runnable.class, 0));
		}});

		Cancellable returned = caller.retryWithBackoff(supplier);

		// When the task runs, the supplier should be called. The supplier
		// returns true, so a retry should be scheduled
		context.checking(new Expectations() {{
			oneOf(supplier).get();
			will(returnValue(true));
			oneOf(taskScheduler).schedule(with(any(Runnable.class)),
					with(ioExecutor), with(MIN_RETRY_INTERVAL_MS),
					with(MILLISECONDS));
			will(new DoAllAction(
					new CaptureArgumentAction<>(runnable, Runnable.class, 0),
					returnValue(scheduledTask)
			));
		}});

		runnable.get().run();

		// When the Cancellable returned by retryWithBackoff() is cancelled,
		// the scheduled task should be cancelled
		context.checking(new Expectations() {{
			oneOf(scheduledTask).cancel();
		}});

		returned.cancel();

		// Cancelling again should have no effect
		returned.cancel();

		// If the scheduled task runs anyway (cancellation came too late),
		// the supplier should not be called and no further tries should be
		// scheduled
		runnable.get().run();
	}

	@Test
	public void testDoublesRetryIntervalUntilMaximumIsReached() {
		// The expected retry intervals increase from the min to the max
		List<Long> expectedIntervals = new ArrayList<>();
		for (long interval = MIN_RETRY_INTERVAL_MS;
				interval <= MAX_RETRY_INTERVAL_MS; interval *= 2) {
			expectedIntervals.add(interval);
		}
		// Once the interval reaches the maximum it should be capped
		expectedIntervals.add(MAX_RETRY_INTERVAL_MS);
		expectedIntervals.add(MAX_RETRY_INTERVAL_MS);

		// Calling retryWithBackoff() should schedule the first try immediately
		AtomicReference<Runnable> runnable = new AtomicReference<>(null);
		context.checking(new Expectations() {{
			oneOf(ioExecutor).execute(with(any(Runnable.class)));
			will(new CaptureArgumentAction<>(runnable, Runnable.class, 0));
		}});

		caller.retryWithBackoff(supplier);

		// Each time the task runs, the supplier returns true, so a retry
		// should be scheduled with a longer interval
		for (long interval : expectedIntervals) {
			context.checking(new Expectations() {{
				oneOf(supplier).get();
				will(returnValue(true));
				oneOf(taskScheduler).schedule(with(any(Runnable.class)),
						with(ioExecutor), with(interval), with(MILLISECONDS));
				will(new DoAllAction(
						new CaptureArgumentAction<>(
								runnable, Runnable.class, 0),
						returnValue(scheduledTask)
				));
			}});

			runnable.get().run();
		}
	}

	// Reify the generic type to mollify jMock
	private interface BooleanSupplier extends Supplier<Boolean> {
	}
}
