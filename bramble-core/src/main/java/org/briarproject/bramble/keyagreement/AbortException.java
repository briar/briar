package org.briarproject.bramble.keyagreement;

class AbortException extends Exception {

	final boolean receivedAbort;

	AbortException() {
		this(false);
	}

	AbortException(boolean receivedAbort) {
		super();
		this.receivedAbort = receivedAbort;
	}

	AbortException(Exception e) {
		this(e, false);
	}

	private AbortException(Exception e, boolean receivedAbort) {
		super(e);
		this.receivedAbort = receivedAbort;
	}
}
