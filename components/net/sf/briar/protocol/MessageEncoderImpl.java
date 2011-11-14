package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.AuthorWriter;
import net.sf.briar.api.protocol.writers.GroupWriter;
import net.sf.briar.api.protocol.writers.MessageEncoder;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class MessageEncoderImpl implements MessageEncoder {

	private final Signature authorSignature, groupSignature;
	private final SecureRandom random;
	private final MessageDigest messageDigest;
	private final WriterFactory writerFactory;
	private final AuthorWriter authorWriter;
	private final GroupWriter groupWriter;

	@Inject
	MessageEncoderImpl(CryptoComponent crypto, WriterFactory writerFactory,
			AuthorWriter authorWriter, GroupWriter groupWriter) {
		authorSignature = crypto.getSignature();
		groupSignature = crypto.getSignature();
		random = crypto.getSecureRandom();
		messageDigest = crypto.getMessageDigest();
		this.writerFactory = writerFactory;
		this.authorWriter = authorWriter;
		this.groupWriter = groupWriter;
	}

	public Message encodeMessage(MessageId parent, String subject, byte[] body)
	throws IOException, GeneralSecurityException {
		return encodeMessage(parent, null, null, null, null, subject, body);
	}

	public Message encodeMessage(MessageId parent, Group group, String subject,
			byte[] body) throws IOException, GeneralSecurityException {
		return encodeMessage(parent, group, null, null, null, subject, body);
	}

	public Message encodeMessage(MessageId parent, Group group,
			PrivateKey groupKey, String subject, byte[] body)
	throws IOException, GeneralSecurityException {
		return encodeMessage(parent, group, groupKey, null, null, subject,
				body);
	}

	public Message encodeMessage(MessageId parent, Group group, Author author,
			PrivateKey authorKey, String subject, byte[] body)
	throws IOException, GeneralSecurityException {
		return encodeMessage(parent, group, null, author, authorKey, subject,
				body);
	}

	public Message encodeMessage(MessageId parent, Group group,
			PrivateKey groupKey, Author author, PrivateKey authorKey,
			String subject, byte[] body) throws IOException,
			GeneralSecurityException {

		if((author == null) != (authorKey == null))
			throw new IllegalArgumentException();
		if((group == null || group.getPublicKey() == null) !=
			(groupKey == null))
			throw new IllegalArgumentException();
		if(subject.getBytes("UTF-8").length > ProtocolConstants.MAX_SUBJECT_LENGTH)
			throw new IllegalArgumentException();
		if(body.length > ProtocolConstants.MAX_BODY_LENGTH)
			throw new IllegalArgumentException();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		// Initialise the consumers
		CountingConsumer counting = new CountingConsumer(
				ProtocolConstants.MAX_PACKET_LENGTH);
		w.addConsumer(counting);
		Consumer digestingConsumer = new DigestingConsumer(messageDigest);
		w.addConsumer(digestingConsumer);
		Consumer authorConsumer = null;
		if(authorKey != null) {
			authorSignature.initSign(authorKey);
			authorConsumer = new SigningConsumer(authorSignature);
			w.addConsumer(authorConsumer);
		}
		Consumer groupConsumer = null;
		if(groupKey != null) {
			groupSignature.initSign(groupKey);
			groupConsumer = new SigningConsumer(groupSignature);
			w.addConsumer(groupConsumer);
		}
		// Write the message
		w.writeUserDefinedId(Types.MESSAGE);
		if(parent == null) w.writeNull();
		else w.writeBytes(parent.getBytes());
		if(group == null) w.writeNull();
		else groupWriter.writeGroup(w, group);
		if(author == null) w.writeNull();
		else authorWriter.writeAuthor(w, author);
		w.writeString(subject);
		long timestamp = System.currentTimeMillis();
		w.writeInt64(timestamp);
		byte[] salt = new byte[ProtocolConstants.SALT_LENGTH];
		random.nextBytes(salt);
		w.writeBytes(salt);
		w.writeBytes(body);
		int bodyStart = (int) counting.getCount() - body.length;
		// Sign the message with the author's private key, if there is one
		if(authorKey == null) {
			w.writeNull();
		} else {
			w.removeConsumer(authorConsumer);
			byte[] sig = authorSignature.sign();
			if(sig.length > ProtocolConstants.MAX_SIGNATURE_LENGTH)
				throw new IllegalArgumentException();
			w.writeBytes(sig);
		}
		// Sign the message with the group's private key, if there is one
		if(groupKey == null) {
			w.writeNull();
		} else {
			w.removeConsumer(groupConsumer);
			byte[] sig = groupSignature.sign();
			if(sig.length > ProtocolConstants.MAX_SIGNATURE_LENGTH)
				throw new IllegalArgumentException();
			w.writeBytes(sig);
		}
		// Hash the message, including the signatures, to get the message ID
		w.removeConsumer(digestingConsumer);
		byte[] raw = out.toByteArray();
		MessageId id = new MessageId(messageDigest.digest());
		GroupId groupId = group == null ? null : group.getId();
		AuthorId authorId = author == null ? null : author.getId();
		return new MessageImpl(id, parent, groupId, authorId, subject,
				timestamp, raw, bodyStart, body.length);
	}
}
