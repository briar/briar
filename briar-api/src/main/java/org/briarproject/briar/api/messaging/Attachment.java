package org.briarproject.briar.api.messaging;

import java.nio.ByteBuffer;

public class Attachment {

	private final ByteBuffer data;

	public Attachment(ByteBuffer data) {
		this.data = data;
	}

	public ByteBuffer getData() {
		return data;
	}

}
