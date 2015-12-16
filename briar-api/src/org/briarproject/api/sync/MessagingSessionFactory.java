package org.briarproject.api.sync;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;

import java.io.InputStream;
import java.io.OutputStream;

public interface MessagingSessionFactory {

	MessagingSession createIncomingSession(ContactId c, TransportId t,
			InputStream in);

	MessagingSession createSimplexOutgoingSession(ContactId c, TransportId t,
			int maxLatency, OutputStream out);

	MessagingSession createDuplexOutgoingSession(ContactId c, TransportId t,
			int maxLatency, int maxIdleTime, OutputStream out);
}
