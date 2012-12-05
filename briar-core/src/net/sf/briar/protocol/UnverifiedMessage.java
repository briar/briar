package net.sf.briar.protocol;

import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.MessageId;

interface UnverifiedMessage {

	MessageId getParent();

	Group getGroup();

	Author getAuthor();

	String getSubject();

	long getTimestamp();

	byte[] getRaw();

	byte[] getAuthorSignature();

	byte[] getGroupSignature();

	int getBodyStart();

	int getBodyLength();

	int getLengthSignedByAuthor();

	int getLengthSignedByGroup();
}