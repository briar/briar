package org.briarproject.api.event;

import org.briarproject.api.keyagreement.KeyAgreementResult;

/** An event that is broadcast when a BQP protocol completes. */
public class KeyAgreementFinishedEvent extends Event {

	private final KeyAgreementResult result;

	public KeyAgreementFinishedEvent(KeyAgreementResult result) {
		this.result = result;
	}

	public KeyAgreementResult getResult() {
		return result;
	}
}
