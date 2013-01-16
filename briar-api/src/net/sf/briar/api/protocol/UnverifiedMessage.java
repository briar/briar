package net.sf.briar.api.protocol;

public interface UnverifiedMessage {

	MessageId getParent();

	Group getGroup();

	Author getAuthor();

	String getSubject();

	long getTimestamp();

	byte[] getSerialised();

	byte[] getAuthorSignature();

	byte[] getGroupSignature();

	int getBodyStart();

	int getBodyLength();

	int getLengthSignedByAuthor();

	int getLengthSignedByGroup();
}