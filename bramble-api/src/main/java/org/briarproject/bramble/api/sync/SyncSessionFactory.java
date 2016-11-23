package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.InputStream;
import java.io.OutputStream;

@NotNullByDefault
public interface SyncSessionFactory {

	SyncSession createIncomingSession(ContactId c, InputStream in);

	SyncSession createSimplexOutgoingSession(ContactId c, int maxLatency,
			OutputStream out);

	SyncSession createDuplexOutgoingSession(ContactId c, int maxLatency,
			int maxIdleTime, OutputStream out);
}
