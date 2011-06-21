package net.sf.briar.api.protocol;


public interface Message {

	MessageId getId();
	MessageId getParent();
	GroupId getGroup();
	AuthorId getAuthor();
	long getTimestamp();
	int getSize();
	byte[] getBody();
}