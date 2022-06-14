package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.event.OwnMailboxConnectionStatusEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executor;

import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.bramble.test.TestUtils.hasEvent;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MailboxManagerImplTest extends BrambleMockTestCase {

	private final Executor ioExecutor = context.mock(Executor.class);
	private final MailboxApi api = context.mock(MailboxApi.class);
	private final TransactionManager db =
			context.mock(TransactionManager.class);
	private final MailboxSettingsManager mailboxSettingsManager =
			context.mock(MailboxSettingsManager.class);
	private final MailboxPairingTaskFactory pairingTaskFactory =
			context.mock(MailboxPairingTaskFactory.class);
	private final Clock clock =
			context.mock(Clock.class);

	private final MailboxManagerImpl manager = new MailboxManagerImpl(
			ioExecutor, api, db, mailboxSettingsManager, pairingTaskFactory,
			clock);

	@Test
	public void testDbExceptionDoesNotRecordFailure() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithNullableResult(with(true),
					withNullableDbCallable(txn));
			oneOf(mailboxSettingsManager).getOwnMailboxProperties(txn);
			will(throwException(new DbException()));
		}});

		assertFalse(manager.checkConnection());
		assertFalse(hasEvent(txn, OwnMailboxConnectionStatusEvent.class));
	}

	@Test
	public void testIOExceptionDoesRecordFailure() throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn2 = new Transaction(null, false);
		MailboxProperties props = getMailboxProperties(true, CLIENT_SUPPORTS);
		long now = new Random().nextLong();

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithNullableResult(with(true),
					withNullableDbCallable(txn));
			oneOf(mailboxSettingsManager).getOwnMailboxProperties(txn);
			will(returnValue(props));
			oneOf(api).getServerSupports(props);
			will(throwException(new IOException()));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(mailboxSettingsManager)
					.recordFailedConnectionAttempt(txn2, now);
		}});

		assertFalse(manager.checkConnection());
	}

	@Test
	public void testApiExceptionDoesRecordFailure() throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn2 = new Transaction(null, false);
		MailboxProperties props = getMailboxProperties(true, CLIENT_SUPPORTS);
		long now = new Random().nextLong();

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithNullableResult(with(true),
					withNullableDbCallable(txn));
			oneOf(mailboxSettingsManager).getOwnMailboxProperties(txn);
			will(returnValue(props));
			oneOf(api).getServerSupports(props);
			will(throwException(new MailboxApi.ApiException()));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(mailboxSettingsManager)
					.recordFailedConnectionAttempt(txn2, now);
		}});

		assertFalse(manager.checkConnection());
	}

	@Test
	public void testConnectionSuccess() throws Exception {
		Transaction txn = new Transaction(null, true);
		Transaction txn2 = new Transaction(null, false);
		MailboxProperties props = getMailboxProperties(true, CLIENT_SUPPORTS);
		long now = new Random().nextLong();

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithNullableResult(with(true),
					withNullableDbCallable(txn));
			oneOf(mailboxSettingsManager).getOwnMailboxProperties(txn);
			will(returnValue(props));
			oneOf(api).getServerSupports(props);
			will(returnValue(CLIENT_SUPPORTS));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(db).transaction(with(false), withDbRunnable(txn2));
			oneOf(mailboxSettingsManager)
					.recordSuccessfulConnection(txn2, now, CLIENT_SUPPORTS);
		}});

		assertTrue(manager.checkConnection());
	}

}
