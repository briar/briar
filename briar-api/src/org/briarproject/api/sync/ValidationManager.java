package org.briarproject.api.sync;

import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;

/**
 * Responsible for managing message validators and passing them messages to
 * validate.
 */
public interface ValidationManager {

	enum State {

		UNKNOWN(0), INVALID(1), PENDING(2), DELIVERED(3);

		private final int value;

		State(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static State fromValue(int value) {
			for (State s : values()) if (s.value == value) return s;
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
		 * Validates the given message and returns its metadata and
		 * dependencies.
		 */
		MessageContext validateMessage(Message m, Group g)
				throws InvalidMessageException;
	}

	interface IncomingMessageHook {

		/**
		 * Called once for each incoming message that passes validation.
		 *
		 * @return whether or not this message should be shared
		 * @throws DbException should only be used for real database errors
		 * @throws InvalidMessageException for any non-database error
		 * that occurs while handling remotely created data.
		 * This includes errors that occur while handling locally created data
		 * in a context controlled by remotely created data
		 * (for example, parsing the metadata of a dependency
		 * of an incoming message).
		 * Throwing this will delete the incoming message and its metadata
		 * marking it as invalid in the database.
		 * Never rethrow DbException as InvalidMessageException
		 */
		boolean incomingMessage(Transaction txn, Message m, Metadata meta)
				throws DbException, InvalidMessageException;
	}
}
