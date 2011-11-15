package net.sf.briar.api.serial;

import net.sf.briar.api.crypto.MessageDigest;

/** A consumer that passes its input through a message digest. */
public class DigestingConsumer implements Consumer {

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
