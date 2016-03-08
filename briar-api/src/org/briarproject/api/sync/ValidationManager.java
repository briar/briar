package org.briarproject.api.sync;

import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;

/**
 * Responsible for managing message validators and passing them messages to
 * validate.
 */
public interface ValidationManager {

	enum Validity {

		UNKNOWN(0), INVALID(1), VALID(2);

		private final int value;

		Validity(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static Validity fromValue(int value) {
			for (Validity s : values()) if (s.value == value) return s;
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Sets the message validator for the given client.
	 */
	void registerMessageValidator(ClientId c, MessageValidator v);

	/**
	 * Sets the incoming message hook for the given client. The hook will be
	 * called once for each incoming message that passes validation.
	 */
	void registerIncomingMessageHook(ClientId c, IncomingMessageHook hook);

	interface MessageValidator {

		/**
		 * Validates the given message and returns its metadata if the message
		 * is valid, or null if the message is invalid.
		 */
		Metadata validateMessage(Message m, Group g);
	}

	interface IncomingMessageHook {

		/**
		 * Called once for each incoming message that passes validation.
		 */
		void incomingMessage(Transaction txn, Message m, Metadata meta)
				throws DbException;
	}
}
