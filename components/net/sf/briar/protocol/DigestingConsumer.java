package net.sf.briar.protocol;

import java.security.MessageDigest;

import net.sf.briar.api.serial.Consumer;

/** A consumer that passes its input through a message digest. */
class DigestingConsumer implements Consumer {

	private final MessageDigest messageDigest;

	DigestingConsumer(MessageDigest messageDigest) {
		this.messageDigest = messageDigest;
	}

	public void write(byte b) {
		messageDigest.update(b);
	}

	public void write(byte[] b, int off, int len) {
		messageDigest.update(b, off, len);
	}
}
