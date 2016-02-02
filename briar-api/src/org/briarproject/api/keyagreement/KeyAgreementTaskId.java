package org.briarproject.api.keyagreement;

import org.briarproject.api.UniqueId;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a BQP task.
 */
public class KeyAgreementTaskId extends UniqueId {

	public KeyAgreementTaskId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof KeyAgreementTaskId && super.equals(o);
	}
}
