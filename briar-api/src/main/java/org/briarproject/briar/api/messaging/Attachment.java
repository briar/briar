package org.briarproject.briar.api.messaging;

import java.io.InputStream;

public class Attachment {

	private final InputStream stream;

	public Attachment(InputStream stream) {
		this.stream = stream;
	}

	public InputStream getStream() {
		return stream;
	}

}
