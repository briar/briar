package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.mailbox.ConnectivityChecker.ConnectivityObserver;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.jmock.Expectations;
import org.jmock.lib.action.DoAllAction;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContactMailboxConnectivityCheckerTest extends BrambleMockTestCase {

	private final Clock clock = context.mock(Clock.class);
	private final MailboxApiCaller mailboxApiCaller =
			context.mock(MailboxApiCaller.class);
	private final MailboxApi mailboxApi = context.mock(MailboxApi.class);
	private final Cancellable task = context.mock(Cancellable.class);
	private final ConnectivityObserver observer =
			context.mock(ConnectivityObserver.class);

	private final MailboxProperties properties =
			getMailboxProperties(false, CLIENT_SUPPORTS);
	private final long now = System.currentTimeMillis();

	@Test
	public void testObserverIsCalledWhenCheckSucceeds() throws Exception {
		ContactMailboxConnectivityChecker checker = createChecker();
		AtomicReference<ApiCall> apiCall = new AtomicReference<>(null);

		// When checkConnectivity() is called a check should be started
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(apiCall, ApiCall.class, 0),
					returnValue(task)
			));
		}});

		checker.checkConnectivity(properties, observer);

		// When the check succeeds the observer should be called
		context.checking(new Expectations() {{
			oneOf(mailboxApi).checkStatus(properties);
			will(returnValue(true));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(observer).onConnectivityCheckSucceeded();
		}});

		// The call should not be retried
		assertFalse(apiCall.get().callApi());
	}

	@Test
	public void testObserverIsNotCalledWhenCheckFails() throws Exception {
		ContactMailboxConnectivityChecker checker = createChecker();
		AtomicReference<ApiCall> apiCall = new AtomicReference<>(null);

		// When checkConnectivity() is called a check should be started
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(apiCall, ApiCall.class, 0),
					returnValue(task)
			));
		}});

		checker.checkConnectivity(properties, observer);

		// When the check fails, the observer should not be called
		context.checking(new Expectations() {{
			oneOf(mailboxApi).checkStatus(properties);
			will(throwException(new IOException()));
		}});

		// The call should be retried
		assertTrue(apiCall.get().callApi());
	}

	private ContactMailboxConnectivityChecker createChecker() {
		return new ContactMailboxConnectivityChecker(clock, mailboxApiCaller,
				mailboxApi);
	}
}
