package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.transport.StreamWriter;

import java.io.InputStream;

import javax.annotation.Nullable;

@NotNullByDefault
public interface SyncSessionFactory {

	SyncSession createIncomingSession(ContactId c, InputStream in,
			PriorityHandler handler);

	SyncSession createSimplexOutgoingSession(ContactId c, TransportId t,
			int maxLatency, StreamWriter streamWriter);

	SyncSession createDuplexOutgoingSession(ContactId c, TransportId t,
			int maxLatency, int maxIdleTime, StreamWriter streamWriter,
			@Nullable Priority priority);
}
