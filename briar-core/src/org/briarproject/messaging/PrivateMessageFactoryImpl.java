package org.briarproject.messaging;

import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static org.briarproject.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;

class PrivateMessageFactoryImpl implements PrivateMessageFactory {

	private final MessageFactory messageFactory;
	private final BdfWriterFactory bdfWriterFactory;

	@Inject
	PrivateMessageFactoryImpl(MessageFactory messageFactory,
			BdfWriterFactory bdfWriterFactory) {
		this.messageFactory = messageFactory;
		this.bdfWriterFactory = bdfWriterFactory;
	}

	@Override
	public PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			MessageId parent, String contentType, byte[] body)
			throws IOException, GeneralSecurityException {
		// Validate the arguments
		if (StringUtils.toUtf8(contentType).length > MAX_CONTENT_TYPE_LENGTH)
			throw new IllegalArgumentException();
		if (body.length > MAX_PRIVATE_MESSAGE_BODY_LENGTH)
			throw new IllegalArgumentException();
		// Serialise the message
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		w.writeListStart();
		if (parent == null) w.writeNull();
		else w.writeRaw(parent.getBytes());
		w.writeString(contentType);
		w.writeRaw(body);
		w.writeListEnd();
		Message m = messageFactory.createMessage(groupId, timestamp,
				out.toByteArray());
		return new PrivateMessage(m, parent, contentType);
	}
}
