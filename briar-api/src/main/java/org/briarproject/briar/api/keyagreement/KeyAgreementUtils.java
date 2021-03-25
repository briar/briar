package org.briarproject.briar.api.keyagreement;

public class KeyAgreementUtils {

	public enum BluetoothDecision {
		/**
		 * We haven't asked the user about Bluetooth discoverability.
		 */
		UNKNOWN,

		/**
		 * The device doesn't have a Bluetooth adapter.
		 */
		NO_ADAPTER,

		/**
		 * We're waiting for the user to accept or refuse discoverability.
		 */
		WAITING,

		/**
		 * The user has accepted discoverability.
		 */
		ACCEPTED,

		/**
		 * The user has refused discoverability.
		 */
		REFUSED
	}

	public enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}
}
