package org.briarproject.bramble.transport.agreement;

import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;

@NotNullByDefault
interface MessageEncoder {

	Message encodeKeyMessage(GroupId contactGroupId,
			TransportId transportId, PublicKey publicKey);

	Message encodeActivateMessage(GroupId contactGroupId,
			TransportId transportId, MessageId previousMessageId);

	BdfDictionary encodeMessageMetadata(TransportId transportId,
			MessageType type, boolean local);
}
