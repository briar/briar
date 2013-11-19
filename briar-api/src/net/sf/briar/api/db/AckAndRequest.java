package net.sf.briar.api.db;

import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.Request;

/**
 * A tuple of an {@link net.sf.briar.api.messaging.Ack} and a
 * {@link net.sf.briar.api.messaging.Request}.
 */
public class AckAndRequest {

	private final Ack ack;
	private final Request request;

	public AckAndRequest(Ack ack, Request request) {
		this.ack = ack;
		this.request = request;
	}

	public Ack getAck() {
		return ack;
	}

	public Request getRequest() {
		return request;
	}
}
