package net.sf.briar.transport;

import javax.crypto.Mac;

import net.sf.briar.api.serial.Consumer;

/** A consumer that passes its input through a MAC. */
class MacConsumer implements Consumer {

	private final Mac mac;

	MacConsumer(Mac mac) {
		this.mac = mac;
	}

	public void write(byte b) {
		mac.update(b);
	}

	public void write(byte[] b) {
		mac.update(b);
	}

	public void write(byte[] b, int off, int len) {
		mac.update(b, off, len);
	}
}
