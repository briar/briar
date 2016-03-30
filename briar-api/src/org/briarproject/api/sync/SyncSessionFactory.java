package org.briarproject.api.sync;

import org.briarproject.api.contact.ContactId;

import java.io.InputStream;
import java.io.OutputStream;

public interface SyncSessionFactory {

	SyncSession createIncomingSession(ContactId c, InputStream in);

	SyncSession createSimplexOutgoingSession(ContactId c, int maxLatency,
			OutputStream out);

	SyncSession createDuplexOutgoingSession(ContactId c, int maxLatency,
			int maxIdleTime, OutputStream out);
}
