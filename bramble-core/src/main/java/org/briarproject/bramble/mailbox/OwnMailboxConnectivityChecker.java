package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxVersion;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@ThreadSafe
@NotNullByDefault
class OwnMailboxConnectivityChecker extends ConnectivityCheckerImpl {

	private static final Logger LOG =
			getLogger(OwnMailboxConnectivityChecker.class.getName());

	private final MailboxApi mailboxApi;
	private final TransactionManager db;
	private final MailboxSettingsManager mailboxSettingsManager;

	@Inject
	OwnMailboxConnectivityChecker(Clock clock,
			MailboxApiCaller mailboxApiCaller,
			MailboxApi mailboxApi,
			TransactionManager db,
			MailboxSettingsManager mailboxSettingsManager) {
		super(clock, mailboxApiCaller);
		this.mailboxApi = mailboxApi;
		this.db = db;
		this.mailboxSettingsManager = mailboxSettingsManager;
	}

	@Override
	ApiCall createConnectivityCheckTask(MailboxProperties properties) {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		return () -> {
			try {
				return checkConnectivityAndStoreResult(properties);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				return true; // Retry
			}
		};
	}

	private boolean checkConnectivityAndStoreResult(
			MailboxProperties properties) throws DbException {
		try {
			LOG.info("Checking whether own mailbox is reachable");
			List<MailboxVersion> serverSupports =
					mailboxApi.getServerSupports(properties);
			LOG.info("Own mailbox is reachable");
			long now = clock.currentTimeMillis();
			db.transaction(false, txn -> mailboxSettingsManager
					.recordSuccessfulConnection(txn, now, serverSupports));
			// Call the observers and cache the result
			onConnectivityCheckSucceeded(now);
			return false; // Don't retry
		} catch (IOException | ApiException e) {
			LOG.warning("Own mailbox is unreachable");
			logException(LOG, WARNING, e);
			long now = clock.currentTimeMillis();
			db.transaction(false, txn -> mailboxSettingsManager
					.recordFailedConnectionAttempt(txn, now));
		}
		return true; // Retry
	}
}
