package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.mailbox.MailboxManager;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class MailboxManagerImpl implements MailboxManager {

	private final Executor ioExecutor;
	private final MailboxSettingsManager mailboxSettingsManager;
	private final MailboxPairingTaskFactory pairingTaskFactory;
	private final Object lock = new Object();

	@Nullable
	@GuardedBy("lock")
	private MailboxPairingTask pairingTask = null;

	@Inject
	MailboxManagerImpl(
			@IoExecutor Executor ioExecutor,
			MailboxSettingsManager mailboxSettingsManager,
			MailboxPairingTaskFactory pairingTaskFactory) {
		this.ioExecutor = ioExecutor;
		this.mailboxSettingsManager = mailboxSettingsManager;
		this.pairingTaskFactory = pairingTaskFactory;
	}

	@Override
	public boolean isPaired(Transaction txn) throws DbException {
		return mailboxSettingsManager.getOwnMailboxProperties(txn) != null;
	}

	@Override
	public MailboxStatus getMailboxStatus(Transaction txn) throws DbException {
		return mailboxSettingsManager.getOwnMailboxStatus(txn);
	}

	@Nullable
	@Override
	public MailboxPairingTask getCurrentPairingTask() {
		synchronized (lock) {
			return pairingTask;
		}
	}

	@Override
	public MailboxPairingTask startPairingTask(String payload) {
		MailboxPairingTask created;
		synchronized (lock) {
			if (pairingTask != null) return pairingTask;
			created = pairingTaskFactory.createPairingTask(payload);
			pairingTask = created;
		}
		ioExecutor.execute(() -> {
			created.run();
			synchronized (lock) {
				// remove task after it finished
				pairingTask = null;
			}
		});
		return created;
	}

}
