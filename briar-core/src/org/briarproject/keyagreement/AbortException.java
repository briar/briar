package org.briarproject.keyagreement;

class AbortException extends Exception {
	public boolean receivedAbort;

	public AbortException() {
		this(false);
	}

	public AbortException(boolean receivedAbort) {
		super();
		this.receivedAbort = receivedAbort;
	}

	public AbortException(Exception e) {
		this(e, false);
	}

	public AbortException(Exception e, boolean receivedAbort) {
		super(e);
		this.receivedAbort = receivedAbort;
	}
}
