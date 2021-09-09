package org.briarproject.bramble.api.sync.validation;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;

public interface IncomingMessageHook {

	/**
	 * Called once for each incoming message that passes validation.
	 * <p>
	 * If an unexpected exception occurs while handling data that is assumed
	 * to be valid (e.g. locally created metadata), it may be sensible to
	 * rethrow the unexpected exception as a DbException so that delivery is
	 * attempted again at next startup. This will allow delivery to succeed if
	 * the unexpected exception was caused by a bug that has subsequently been
	 * fixed.
	 *
	 * @param txn A read-write transaction
	 * @throws DbException if a database error occurs while delivering the
	 * message. Delivery will be attempted again at next startup. Throwing
	 * this exception has the same effect as returning
	 * {@link DeliveryAction#DEFER}.
	 * @throws InvalidMessageException if the message is invalid in the context
	 * of its dependencies. The message and any dependents will be marked as
	 * invalid and deleted along with their metadata. Throwing this exception
	 * has the same effect as returning {@link DeliveryAction#REJECT}.
	 */
	DeliveryAction incomingMessage(Transaction txn, Message m, Metadata meta)
			throws DbException, InvalidMessageException;

	enum DeliveryAction {

		/**
		 * The message and any dependent messages will be moved to the
		 * {@link MessageState#INVALID INVALID state} and deleted, along with
		 * their metadata.
		 */
		REJECT,

		/**
		 * The message will be moved to the
		 * {@link MessageState#PENDING PENDING state}. Delivery will be
		 * attempted again at next startup.
		 */
		DEFER,

		/**
		 * The message will be moved to the
		 * {@link MessageState#DELIVERED DELIVERED state} and shared.
		 */
		ACCEPT_SHARE,

		/**
		 * The message will be moved to the
		 * {@link MessageState#DELIVERED DELIVERED state} and will not be
		 * shared.
		 */
		ACCEPT_DO_NOT_SHARE
	}
}
