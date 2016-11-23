package org.briarproject.bramble.api.reliability;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A protocol layer that attempts to ensure reliable, ordered delivery of data
 * across an unreliable lower layer. Interactions with the lower layer use the
 * buffer-oriented {@link ReadHandler} and {@link WriteHandler} interfaces; the
 * reliability layer presents stream-oriented {@link InputStream} and
 * {@link OutputStream} interfaces to higher layers.
 */
@NotNullByDefault
public interface ReliabilityLayer extends ReadHandler {

	/**
	 * Starts the reliability layer.
	 */
	void start();

	/**
	 * Stops the reliability layer. After this method returns, no more data
	 * will be sent to lower layers, and any data received from lower layers
	 * will be ignored.
	 */
	void stop();

	/**
	 * Returns an input stream for higher layers to read from the reliability
	 * layer.
	 */
	InputStream getInputStream();

	/**
	 * Returns an output stream for higher layers to write to the reliability
	 * layer.
	 */
	OutputStream getOutputStream();
}
