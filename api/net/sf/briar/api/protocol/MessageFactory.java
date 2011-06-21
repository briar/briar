package net.sf.briar.api.protocol;

public interface MessageFactory {

	Message createMessage(MessageId id, MessageId parent, GroupId group,
			AuthorId author, long timestamp, byte[] body);
}
