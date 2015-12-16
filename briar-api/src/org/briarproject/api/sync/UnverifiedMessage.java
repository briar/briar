package org.briarproject.api.sync;

import org.briarproject.api.identity.Author;

/** A {@link Message} that has not yet had its signatures (if any) verified. */
public class UnverifiedMessage {

	private final MessageId parent;
	private final Group group;
	private final Author author;
	private final String contentType;
	private final long timestamp;
	private final byte[] raw, signature;
	private final int bodyStart, bodyLength, signedLength;

	public UnverifiedMessage(MessageId parent, Group group, Author author,
			String contentType, long timestamp, byte[] raw, byte[] signature,
			int bodyStart, int bodyLength, int signedLength) {
		this.parent = parent;
		this.group = group;
		this.author = author;
		this.contentType = contentType;
		this.timestamp = timestamp;
		this.raw = raw;
		this.signature = signature;
		this.bodyStart = bodyStart;
		this.bodyLength = bodyLength;
		this.signedLength = signedLength;
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
	 * Returns the message's {@link Author Author}, or null
	 * if this is an anonymous message.
	 */
	public Author getAuthor() {
		return author;
	}

	/** Returns the message's content type. */
	public String getContentType() {
		return contentType;
	}

	/** Returns the message's timestamp. */
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
	public byte[] getSignature() {
		return signature;
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
	public int getSignedLength() {
		return signedLength;
	}
}