package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet acknowledging receipt of one or more messages. */
public interface Ack {

	/** Returns the IDs of the acknowledged messages. */
	Collection<MessageId> getMessageIds();
}
