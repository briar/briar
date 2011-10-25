package net.sf.briar.api.protocol;

public interface MessageHeaderFactory {

	MessageHeader createMessageHeader(MessageId id, MessageId parent,
			GroupId group, AuthorId author, String subject, long timestamp);
}
