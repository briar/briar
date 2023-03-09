package org.briarproject.bramble.mailbox;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

import static java.lang.System.currentTimeMillis;
import static org.briarproject.bramble.mailbox.AbstractMailboxIntegrationTest.URL_BASE;
import static org.briarproject.bramble.mailbox.MailboxTestUtils.createHttpClientProvider;
import static org.junit.Assert.fail;

class MailboxIntegrationTestUtils {

	static MailboxApi createMailboxApi(IntSupplier portSupplier) {
		UrlConverter urlConverter = onion -> {
			int port = portSupplier.getAsInt(); //only access when needed
			return URL_BASE + ":" + port;
		};
		return new MailboxApiImpl(createHttpClientProvider(), urlConverter);
	}

	interface Check {
		boolean check() throws Exception;
	}

	/**
	 * Run the specified method {@code check} every {@code step} milliseconds
	 * until either {@code check} returns true or longer than {@code totalTime}
	 * milliseconds have been spent checking the function and waiting for the
	 * next invocation.
	 */
	static void retryUntilSuccessOrTimeout(long totalTime, long step,
			Check check) throws Exception {
		AtomicBoolean success = new AtomicBoolean(false);

		checkRepeatedly(totalTime, step, () -> {
					boolean result = check.check();
					if (result) success.set(true);
					return result;
				}
		);

		if (!success.get()) {
			fail("timeout reached");
		}
	}

	/**
	 * Run the specified method {@code check} every {@code step} milliseconds
	 * until either {@code check} returns true or longer than {@code totalTime}
	 * milliseconds have been spent checking the function and waiting for the
	 * next invocation.
	 */
	private static void checkRepeatedly(long totalTime, long step,
			Check check) throws Exception {
		long start = currentTimeMillis();
		while (currentTimeMillis() - start < totalTime) {
			if (check.check()) {
				return;
			}
			try {
				//noinspection BusyWait
				Thread.sleep(step);
			} catch (InterruptedException ignore) {
				// continue
			}
		}
	}
}
