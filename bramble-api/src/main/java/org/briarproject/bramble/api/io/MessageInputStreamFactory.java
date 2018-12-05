package org.briarproject.bramble.api.io;

import org.briarproject.bramble.api.sync.MessageId;

import java.io.IOException;
import java.io.InputStream;

public interface MessageInputStreamFactory {

	/**
	 * Returns an {@link InputStream} for reading the given message from the
	 * database. This method returns immediately. If the message is not in the
	 * database or cannot be read, reading from the stream will throw an
	 * {@link IOException};
	 */
	InputStream getMessageInputStream(MessageId m);
}
