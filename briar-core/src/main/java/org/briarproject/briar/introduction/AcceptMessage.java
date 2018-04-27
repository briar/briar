package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.SessionId;

import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class AcceptMessage extends AbstractIntroductionMessage {

	private final SessionId sessionId;
	private final byte[] ephemeralPublicKey;
	private final long acceptTimestamp;
	private final Map<TransportId, TransportProperties> transportProperties;

	protected AcceptMessage(MessageId messageId, GroupId groupId,
			long timestamp, @Nullable MessageId previousMessageId,
			SessionId sessionId,
			byte[] ephemeralPublicKey,
			long acceptTimestamp,
			Map<TransportId, TransportProperties> transportProperties) {
		super(messageId, groupId, timestamp, previousMessageId);
		this.sessionId = sessionId;
		this.ephemeralPublicKey = ephemeralPublicKey;
		this.acceptTimestamp = acceptTimestamp;
		this.transportProperties = transportProperties;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public byte[] getEphemeralPublicKey() {
		return ephemeralPublicKey;
	}

	public long getAcceptTimestamp() {
		return acceptTimestamp;
	}

	public Map<TransportId, TransportProperties> getTransportProperties() {
		return transportProperties;
	}

}
