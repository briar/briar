package org.briarproject.api.event;

/** An event that is broadcast when a BQP protocol aborts. */
public class KeyAgreementAbortedEvent extends Event {

	private final boolean remoteAborted;

	public KeyAgreementAbortedEvent(boolean remoteAborted) {
		this.remoteAborted = remoteAborted;
	}

	public boolean didRemoteAbort() {
		return remoteAborted;
	}
}
