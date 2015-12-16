package org.briarproject.sync;

import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.data.Consumer;

/** A consumer that passes its input through a message digest. */
class DigestingConsumer implements Consumer {

	private final MessageDigest messageDigest;

	public DigestingConsumer(MessageDigest messageDigest) {
		this.messageDigest = messageDigest;
	}

	public void write(byte b) {
		messageDigest.update(b);
	}

	public void write(byte[] b, int off, int len) {
		messageDigest.update(b, off, len);
	}
}
