package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.MessageId;

class AckImpl implements Ack {

	private final Collection<MessageId> acked;

	AckImpl(Collection<MessageId> acked) {
		this.acked = acked;
	}

	public Collection<MessageId> getMessageIds() {
		return acked;
	}
}
