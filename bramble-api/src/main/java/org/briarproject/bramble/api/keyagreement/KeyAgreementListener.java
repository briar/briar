package org.briarproject.bramble.api.keyagreement;

import org.briarproject.bramble.api.data.BdfList;

import java.io.IOException;

/**
 * An class for managing a particular key agreement listener.
 */
public abstract class KeyAgreementListener {

	private final BdfList descriptor;

	public KeyAgreementListener(BdfList descriptor) {
		this.descriptor = descriptor;
	}

	/**
	 * Returns the descriptor that a remote peer can use to connect to this
	 * listener.
	 */
	public BdfList getDescriptor() {
		return descriptor;
	}

	/**
	 * Blocks until an incoming connection is received and returns it.
	 *
	 * @throws IOException if an error occurs or {@link #close()} is called.
	 */
	public abstract KeyAgreementConnection accept() throws IOException;

	/**
	 * Closes the underlying server socket.
	 */
	public abstract void close();
}
