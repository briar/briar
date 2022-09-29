package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.InputStream;

import javax.annotation.Nullable;

@NotNullByDefault
public interface SyncSessionFactory {

	/**
	 * Creates a session for receiving data from a contact.
	 */
	SyncSession createIncomingSession(ContactId c, InputStream in,
			PriorityHandler handler);

	/**
	 * Creates a session for sending data to a contact over a simplex transport.
	 *
	 * @param eager True if messages should be sent eagerly, ie regardless of
	 * whether they're due for retransmission.
	 */
	SyncSession createSimplexOutgoingSession(ContactId c, TransportId t,
			long maxLatency, boolean eager, StreamWriter streamWriter);

	/**
	 * Creates a session for sending data to a contact via a mailbox. The IDs
	 * of any messages sent or acked will be added to the given
	 * {@link OutgoingSessionRecord}.
	 */
	SyncSession createSimplexOutgoingSession(ContactId c, TransportId t,
			long maxLatency, StreamWriter streamWriter,
			OutgoingSessionRecord sessionRecord);

	SyncSession createDuplexOutgoingSession(ContactId c, TransportId t,
			long maxLatency, int maxIdleTime, StreamWriter streamWriter,
			@Nullable Priority priority);
}
