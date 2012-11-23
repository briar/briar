package net.sf.briar.plugins.modem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

interface Modem {

	/**
	 * Call this method once after creating the modem. If an exception is
	 * thrown, the modem cannot be used.
	 */
	void init() throws IOException;

	/**
	 * Initiates an outgoing call and returns true if the call connects. If the
	 * call does not connect the modem is hung up.
	 */
	boolean dial(String number) throws IOException;

	/** Returns a stream for reading from the currently connected call. */
	InputStream getInputStream();

	/** Returns a stream for writing to the currently connected call. */
	OutputStream getOutputStream();

	/** Hangs up the modem, ending the currently connected call. */
	void hangUp() throws IOException;

	interface Callback {

		/** Called when an incoming call connects. */
		void incomingCallConnected();
	}
}
