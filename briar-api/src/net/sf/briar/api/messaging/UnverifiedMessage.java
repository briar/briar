package net.sf.briar.api.messaging;

/** A {@link Message} that has not yet had its signatures verified. */
public class UnverifiedMessage {

	private final MessageId parent;
	private final Group group;
	private final Author author;
	private final String contentType, subject;
	private final long timestamp;
	private final byte[] raw, authorSig, groupSig;
	private final int bodyStart, bodyLength, signedByAuthor, signedByGroup;

	public UnverifiedMessage(MessageId parent, Group group, Author author,
			String contentType, String subject, long timestamp, byte[] raw,
			byte[] authorSig, byte[] groupSig, int bodyStart, int bodyLength,
			int signedByAuthor, int signedByGroup) {
		this.parent = parent;
		this.group = group;
		this.author = author;
		this.contentType = contentType;
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

	/**
	 * Returns the identifier of the message's parent, or null if this is the
	 * first message in a thread.
	 */
	public MessageId getParent() {
		return parent;
	}

	/**
	 * Returns the {@link Group} to which the message belongs, or null if this
	 * is a private message.
	 */
	public Group getGroup() {
		return group;
	}

	/**
	 * Returns the message's {@link Author}, or null if this is an anonymous
	 * message.
	 */
	public Author getAuthor() {
		return author;
	}

	/** Returns the message's content type. */
	public String getContentType() {
		return contentType;
	}

	/**
	 * Returns the message's subject line, which is created from the first 50
	 * bytes of the message body if the content type is text/plain, or is the
	 * empty string otherwise.
	 */
	public String getSubject() {
		return subject;
	}

	/** Returns the timestamp created by the message's {@link Author}. */
	public long getTimestamp() {
		return timestamp;
	}

	/** Returns the serialised message. */
	public byte[] getSerialised() {
		return raw;
	}

	/**
	 * Returns the author's signature, or null if this is an anonymous message.
	 */
	public byte[] getAuthorSignature() {
		return authorSig;
	}

	/**
	 * Returns the group's signature, or null if this is a private message or
	 * a message belonging to an unrestricted group.
	 */
	public byte[] getGroupSignature() {
		return groupSig;
	}

	/** Returns the offset of the message body within the serialised message. */
	public int getBodyStart() {
		return bodyStart;
	}

	/** Returns the length of the message body in bytes. */
	public int getBodyLength() {
		return bodyLength;
	}

	/**
	 * Returns the length in bytes of the data covered by the author's
	 * signature.
	 */
	public int getLengthSignedByAuthor() {
		return signedByAuthor;
	}

	/**
	 * Returns the length in bytes of the data covered by the group's
	 * signature.
	 */
	public int getLengthSignedByGroup() {
		return signedByGroup;
	}
}