package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.mailbox.ConnectivityChecker.ConnectivityObserver;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import javax.annotation.Nonnull;

import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.mailbox.ConnectivityCheckerImpl.CONNECTIVITY_CHECK_FRESHNESS_MS;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;

public class ConnectivityCheckerImplTest extends BrambleMockTestCase {

	private final Clock clock = context.mock(Clock.class);
	private final MailboxApiCaller mailboxApiCaller =
			context.mock(MailboxApiCaller.class);
	private final ApiCall apiCall = context.mock(ApiCall.class);
	private final Cancellable task = context.mock(Cancellable.class);
	private final ConnectivityObserver observer1 =
			context.mock(ConnectivityObserver.class, "1");
	private final ConnectivityObserver observer2 =
			context.mock(ConnectivityObserver.class, "2");

	private final MailboxProperties properties =
			getMailboxProperties(true, CLIENT_SUPPORTS);
	private final long now = System.currentTimeMillis();

	@Test
	public void testFirstObserverStartsCheck() {
		ConnectivityCheckerImpl checker = createChecker();

		// When checkConnectivity() is called a check should be started
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(mailboxApiCaller).retryWithBackoff(apiCall);
			will(returnValue(task));
		}});

		checker.checkConnectivity(properties, observer1);

		// When the check succeeds the observer should be called
		context.checking(new Expectations() {{
			oneOf(observer1).onConnectivityCheckSucceeded();
		}});

		checker.onConnectivityCheckSucceeded(now);

		// The observer should not be called again when subsequent checks
		// succeed
		checker.onConnectivityCheckSucceeded(now);
	}

	@Test
	public void testObserverIsAddedToExistingCheck() {
		ConnectivityCheckerImpl checker = createChecker();

		// When checkConnectivity() is called a check should be started
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(mailboxApiCaller).retryWithBackoff(apiCall);
			will(returnValue(task));
		}});

		checker.checkConnectivity(properties, observer1);

		// When checkConnectivity() is called again before the first check
		// succeeds, the observer should be added to the existing check
		checker.checkConnectivity(properties, observer2);

		// When the check succeeds both observers should be called
		context.checking(new Expectations() {{
			oneOf(observer1).onConnectivityCheckSucceeded();
			oneOf(observer2).onConnectivityCheckSucceeded();
		}});

		checker.onConnectivityCheckSucceeded(now);

		// The observers should not be called again when subsequent checks
		// succeed
		checker.onConnectivityCheckSucceeded(now);
	}

	@Test
	public void testFreshResultIsReused() {
		ConnectivityCheckerImpl checker = createChecker();

		// When checkConnectivity() is called a check should be started
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(mailboxApiCaller).retryWithBackoff(apiCall);
			will(returnValue(task));
		}});

		checker.checkConnectivity(properties, observer1);

		// When the check succeeds the observer should be called
		context.checking(new Expectations() {{
			oneOf(observer1).onConnectivityCheckSucceeded();
		}});

		checker.onConnectivityCheckSucceeded(now);

		// When checkConnectivity() is called again within
		// CONNECTIVITY_CHECK_FRESHNESS_MS the observer should be called with
		// the result of the recent check
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now + CONNECTIVITY_CHECK_FRESHNESS_MS));
			oneOf(observer2).onConnectivityCheckSucceeded();
		}});

		checker.checkConnectivity(properties, observer2);
	}

	@Test
	public void testStaleResultIsNotReused() {
		ConnectivityCheckerImpl checker = createChecker();

		// When checkConnectivity() is called a check should be started
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(mailboxApiCaller).retryWithBackoff(apiCall);
			will(returnValue(task));
		}});

		checker.checkConnectivity(properties, observer1);

		// When the check succeeds the observer should be called
		context.checking(new Expectations() {{
			oneOf(observer1).onConnectivityCheckSucceeded();
		}});

		checker.onConnectivityCheckSucceeded(now);

		// When checkConnectivity() is called again after more than
		// CONNECTIVITY_CHECK_FRESHNESS_MS another check should be started
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now + CONNECTIVITY_CHECK_FRESHNESS_MS + 1));
			oneOf(mailboxApiCaller).retryWithBackoff(apiCall);
			will(returnValue(task));
		}});

		checker.checkConnectivity(properties, observer2);

		// When the check succeeds the observer should be called
		context.checking(new Expectations() {{
			oneOf(observer2).onConnectivityCheckSucceeded();
		}});

		checker.onConnectivityCheckSucceeded(
				now + CONNECTIVITY_CHECK_FRESHNESS_MS + 1);
	}

	@Test
	public void testCheckIsCancelledWhenCheckerIsDestroyed() {
		ConnectivityCheckerImpl checker = createChecker();

		// When checkConnectivity() is called a check should be started
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(mailboxApiCaller).retryWithBackoff(apiCall);
			will(returnValue(task));
		}});

		checker.checkConnectivity(properties, observer1);

		// When the checker is destroyed the check should be cancelled
		context.checking(new Expectations() {{
			oneOf(task).cancel();
		}});

		checker.destroy();

		// If the check runs anyway (cancellation came too late) the observer
		// should not be called
		checker.onConnectivityCheckSucceeded(now);
	}

	@Test
	public void testCheckIsCancelledWhenObserverIsRemoved() {
		ConnectivityCheckerImpl checker = createChecker();

		// When checkConnectivity() is called a check should be started
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(mailboxApiCaller).retryWithBackoff(apiCall);
			will(returnValue(task));
		}});

		checker.checkConnectivity(properties, observer1);

		// When the observer is removed the check should be cancelled
		context.checking(new Expectations() {{
			oneOf(task).cancel();
		}});

		checker.removeObserver(observer1);

		// If the check runs anyway (cancellation came too late) the observer
		// should not be called
		checker.onConnectivityCheckSucceeded(now);
	}

	private ConnectivityCheckerImpl createChecker() {

		return new ConnectivityCheckerImpl(clock, mailboxApiCaller) {
			@Override
			@Nonnull
			protected ApiCall createConnectivityCheckTask(
					@Nonnull MailboxProperties properties) {
				return apiCall;
			}
		};
	}
}
