package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;

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

}
