package org.briarproject.bramble.api.keyagreement.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that is broadcast when a BQP task is listening.
 */
@Immutable
@NotNullByDefault
public class KeyAgreementListeningEvent extends Event {

	private final Payload localPayload;

	public KeyAgreementListeningEvent(Payload localPayload) {
		this.localPayload = localPayload;
	}

	public Payload getLocalPayload() {
		return localPayload;
	}
}
