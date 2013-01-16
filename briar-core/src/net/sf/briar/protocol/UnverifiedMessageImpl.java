package net.sf.briar.protocol;

import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.UnverifiedMessage;

class UnverifiedMessageImpl implements UnverifiedMessage {

	private final MessageId parent;
	private final Group group;
	private final Author author;
	private final String subject;
	private final long timestamp;
	private final byte[] raw, authorSig, groupSig;
	private final int bodyStart, bodyLength, signedByAuthor, signedByGroup;

	UnverifiedMessageImpl(MessageId parent, Group group, Author author,
			String subject, long timestamp, byte[] raw, byte[] authorSig,
			byte[] groupSig, int bodyStart, int bodyLength, int signedByAuthor,
			int signedByGroup) {
		this.parent = parent;
		this.group = group;
		this.author = author;
		this.subject = subject;
		this.timestamp = timestamp;
		this.raw = raw;
		this.authorSig = authorSig;
		this.groupSig = groupSig;
		this.bodyStart = bodyStart;
		this.bodyLength = bodyLength;
		this.signedByAuthor = signedByAuthor;
		this.signedByGroup = signedByGroup;
	}

	public MessageId getParent() {
		return parent;
	}

	public Group getGroup() {
		return group;
	}

	public Author getAuthor() {
		return author;
	}

	public String getSubject() {
		return subject;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public byte[] getSerialised() {
		return raw;
	}

	public byte[] getAuthorSignature() {
		return authorSig;
	}

	public byte[] getGroupSignature() {
		return groupSig;
	}

	public int getBodyStart() {
		return bodyStart;
	}

	public int getBodyLength() {
		return bodyLength;
	}

	public int getLengthSignedByAuthor() {
		return signedByAuthor;
	}

	public int getLengthSignedByGroup() {
		return signedByGroup;
	}
}
