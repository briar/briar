package org.briarproject.api.sync;

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

	/** Sets the message validator for the given client. */
	void registerMessageValidator(ClientId c, MessageValidator v);
}
