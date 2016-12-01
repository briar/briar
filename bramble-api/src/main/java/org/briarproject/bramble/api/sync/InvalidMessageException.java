package org.briarproject.bramble.api.sync;

import java.io.IOException;

/**
 * An exception that indicates an invalid message.
 */
public class InvalidMessageException extends IOException {

	public InvalidMessageException() {
		super();
	}

	public InvalidMessageException(String str) {
		super(str);
	}

	public InvalidMessageException(Throwable t) {
		super(t);
	}

}
