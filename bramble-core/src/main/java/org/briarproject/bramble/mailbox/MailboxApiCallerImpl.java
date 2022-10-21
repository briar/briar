package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Immutable
@NotNullByDefault
class MailboxApiCallerImpl implements MailboxApiCaller {

	private final TaskScheduler taskScheduler;
	private final MailboxConfig mailboxConfig;
	private final Executor ioExecutor;

	@Inject
	MailboxApiCallerImpl(TaskScheduler taskScheduler,
			MailboxConfig mailboxConfig,
			@IoExecutor Executor ioExecutor) {
		this.taskScheduler = taskScheduler;
		this.mailboxConfig = mailboxConfig;
		this.ioExecutor = ioExecutor;
	}

	@Override
	public Cancellable retryWithBackoff(ApiCall apiCall) {
		Task task = new Task(apiCall);
		task.start();
		return task;
	}

	private class Task implements Cancellable {

		private final ApiCall apiCall;
		private final Object lock = new Object();

		@GuardedBy("lock")
		@Nullable
		private Cancellable scheduledTask = null;

		@GuardedBy("lock")
		private boolean cancelled = false;

		@GuardedBy("lock")
		private long retryIntervalMs =
				mailboxConfig.getApiCallerMinRetryInterval();

		private Task(ApiCall apiCall) {
			this.apiCall = apiCall;
		}

		private void start() {
			synchronized (lock) {
				if (cancelled) throw new AssertionError();
				ioExecutor.execute(this::callApi);
			}
		}

		@IoExecutor
		private void callApi() {
			synchronized (lock) {
				if (cancelled) return;
			}
			// The call returns true if we should retry
			if (apiCall.callApi()) {
				synchronized (lock) {
					if (cancelled) return;
					scheduledTask = taskScheduler.schedule(this::callApi,
							ioExecutor, retryIntervalMs, MILLISECONDS);
					// Increase the retry interval each time we retry
					retryIntervalMs = min(
							mailboxConfig.getApiCallerMaxRetryInterval(),
							retryIntervalMs * 2);
				}
			} else {
				synchronized (lock) {
					scheduledTask = null;
				}
			}
		}

		@Override
		public void cancel() {
			Cancellable scheduledTask;
			synchronized (lock) {
				cancelled = true;
				scheduledTask = this.scheduledTask;
				this.scheduledTask = null;
			}
			if (scheduledTask != null) scheduledTask.cancel();
		}
	}
}
