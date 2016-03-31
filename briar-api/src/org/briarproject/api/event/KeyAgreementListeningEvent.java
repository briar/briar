package org.briarproject.api.event;

import org.briarproject.api.keyagreement.Payload;

/** An event that is broadcast when a BQP task is listening. */
public class KeyAgreementListeningEvent extends Event {

	private final Payload localPayload;

	public KeyAgreementListeningEvent(Payload localPayload) {
		this.localPayload = localPayload;
	}

	public Payload getLocalPayload() {
		return localPayload;
	}
}
