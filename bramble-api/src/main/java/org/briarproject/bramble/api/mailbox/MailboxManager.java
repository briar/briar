package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.Consumer;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.IoExecutor;

import javax.annotation.Nullable;

public interface MailboxManager {

	/**
	 * @return true if a mailbox is already paired.
	 */
	boolean isPaired(Transaction txn) throws DbException;

	/**
	 * @return the current status of the mailbox.
	 */
	MailboxStatus getMailboxStatus(Transaction txn) throws DbException;

	/**
	 * Returns the currently running pairing task,
	 * or null if no pairing task is running.
	 */
	@Nullable
	MailboxPairingTask getCurrentPairingTask();

	/**
	 * Starts and returns a pairing task. If a pairing task is already running,
	 * it will be returned and the argument will be ignored.
	 *
	 * @param qrCodePayload The ISO-8859-1 encoded bytes of the mailbox QR code.
	 */
	MailboxPairingTask startPairingTask(String qrCodePayload);

	/**
	 * Can be used by the UI to test the mailbox connection.
	 * After the connection has been made, the given {@param connectionCallback}
	 * will be called with true (success) or false (error).
	 * In addition, a {@link OwnMailboxConnectionStatusEvent} might be broadcast
	 * with a new {@link MailboxStatus}.
	 * <p>
	 * Note that the callback will be made on the {@link IoExecutor}.
	 */
	void checkConnection(Consumer<Boolean> connectionCallback);

}
