package org.briarproject.briar.introduction2;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.briar.api.client.SessionId;

interface MessageEncoder {

	BdfDictionary encodeRequestMetadata(MessageType type,
			long timestamp, boolean local, boolean read, boolean visible,
			boolean available, boolean accepted);

	BdfDictionary encodeMetadata(MessageType type, SessionId sessionId,
			long timestamp, boolean local, boolean read, boolean visible);

}
