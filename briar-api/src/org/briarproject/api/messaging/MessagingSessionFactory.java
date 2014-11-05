package org.briarproject.api.messaging;

import java.io.InputStream;
import java.io.OutputStream;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;

public interface MessagingSessionFactory {

	MessagingSession createIncomingSession(ContactId c, InputStream in);

	MessagingSession createOutgoingSession(ContactId c, TransportId t,
			long maxLatency, OutputStream out, boolean duplex);
}
