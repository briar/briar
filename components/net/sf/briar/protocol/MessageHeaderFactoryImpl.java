package net.sf.briar.protocol;

import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.MessageHeader;
import net.sf.briar.api.protocol.MessageHeaderFactory;
import net.sf.briar.api.protocol.MessageId;

class MessageHeaderFactoryImpl implements MessageHeaderFactory {

	public MessageHeader createMessageHeader(MessageId id, MessageId parent,
			GroupId group, AuthorId author, String subject, long timestamp) {
		return new MessageHeaderImpl(id, parent, group, author, subject,
				timestamp);
	}
}
