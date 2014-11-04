package org.briarproject.api.messaging;

import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.transport.StreamContext;

public interface MessagingSessionFactory {

	MessagingSession createIncomingSession(StreamContext ctx,
			TransportConnectionReader r);

	MessagingSession createOutgoingSession(StreamContext ctx,
			TransportConnectionWriter w, boolean duplex);
}
