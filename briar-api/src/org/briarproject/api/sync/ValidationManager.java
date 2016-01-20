package org.briarproject.api.sync;

/**
 * Responsible for managing message validators and passing them messages to
 * validate.
 */
public interface ValidationManager {

	enum Status {

		UNKNOWN(0), INVALID(1), VALID(2);

		private final int value;

		Status(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static Status fromValue(int value) {
			for (Status s : values()) if (s.value == value) return s;
			throw new IllegalArgumentException();
		}
	}

	/** Sets the message validator for the given client. */
	void registerMessageValidator(ClientId c, MessageValidator v);
}
