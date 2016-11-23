package org.briarproject.bramble.api.keyagreement;

import org.briarproject.bramble.api.data.BdfList;

import java.util.concurrent.Callable;

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
	 * Starts listening for incoming connections, and returns a Callable that
	 * will return a KeyAgreementConnection when an incoming connection is
	 * received.
	 */
	public abstract Callable<KeyAgreementConnection> listen();

	/**
	 * Closes the underlying server socket.
	 */
	public abstract void close();
}
