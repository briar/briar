package net.sf.briar.messaging;

import static net.sf.briar.api.messaging.MessagingConstants.MAX_BODY_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_SIGNATURE_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_SUBJECT_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.SALT_LENGTH;
import static net.sf.briar.api.messaging.Types.AUTHOR;
import static net.sf.briar.api.messaging.Types.GROUP;
import static net.sf.briar.api.messaging.Types.MESSAGE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.DigestingConsumer;
import net.sf.briar.api.serial.SigningConsumer;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class MessageFactoryImpl implements MessageFactory {

	private final Signature authorSignature, groupSignature;
	private final SecureRandom random;
	private final MessageDigest messageDigest;
	private final WriterFactory writerFactory;
	private final Clock clock;
	private final CharsetDecoder decoder;

	@Inject
	MessageFactoryImpl(CryptoComponent crypto, WriterFactory writerFactory,
			Clock clock) {
		authorSignature = crypto.getSignature();
		groupSignature = crypto.getSignature();
		random = crypto.getSecureRandom();
		messageDigest = crypto.getMessageDigest();
		this.writerFactory = writerFactory;
		this.clock = clock;
		decoder = Charset.forName("UTF-8").newDecoder();
	}

	public Message createPrivateMessage(MessageId parent, String contentType,
			byte[] body) throws IOException, GeneralSecurityException {
		return createMessage(parent, null, null, null, null, contentType, body);
	}

	public Message createAnonymousMessage(MessageId parent, Group group,
			String contentType, byte[] body) throws IOException,
			GeneralSecurityException {
		return createMessage(parent, group, null, null, null, contentType,
				body);
	}

	public Message createAnonymousMessage(MessageId parent, Group group,
			PrivateKey groupKey, String contentType, byte[] body)
					throws IOException, GeneralSecurityException {
		return createMessage(parent, group, groupKey, null, null, contentType,
				body);
	}

	public Message createPseudonymousMessage(MessageId parent, Group group,
			Author author, PrivateKey authorKey, String contentType,
			byte[] body) throws IOException, GeneralSecurityException {
		return createMessage(parent, group, null, author, authorKey,
				contentType, body);
	}

	public Message createPseudonymousMessage(MessageId parent, Group group,
			PrivateKey groupKey, Author author, PrivateKey authorKey,
			String contentType, byte[] body) throws IOException,
			GeneralSecurityException {
		return createMessage(parent, group, groupKey, author, authorKey,
				contentType, body);
	}

	private Message createMessage(MessageId parent, Group group,
			PrivateKey groupKey, Author author, PrivateKey authorKey,
			String contentType, byte[] body) throws IOException,
			GeneralSecurityException {
		// Validate the arguments
		if((author == null) != (authorKey == null))
			throw new IllegalArgumentException();
		if((group == null || group.getPublicKey() == null)
				!= (groupKey == null))
			throw new IllegalArgumentException();
		if(contentType.getBytes("UTF-8").length > MAX_CONTENT_TYPE_LENGTH)
			throw new IllegalArgumentException();
		if(body.length > MAX_BODY_LENGTH)
			throw new IllegalArgumentException();
		// Serialise the message to a buffer
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		// Initialise the consumers
		CountingConsumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
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
		w.writeStructId(MESSAGE);
		if(parent == null) w.writeNull();
		else w.writeBytes(parent.getBytes());
		if(group == null) w.writeNull();
		else writeGroup(w, group);
		if(author == null) w.writeNull();
		else writeAuthor(w, author);
		w.writeString(contentType);
		long timestamp = clock.currentTimeMillis();
		w.writeInt64(timestamp);
		byte[] salt = new byte[SALT_LENGTH];
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
			if(sig.length > MAX_SIGNATURE_LENGTH)
				throw new IllegalArgumentException();
			w.writeBytes(sig);
		}
		// Sign the message with the group's private key, if there is one
		if(groupKey == null) {
			w.writeNull();
		} else {
			w.removeConsumer(groupConsumer);
			byte[] sig = groupSignature.sign();
			if(sig.length > MAX_SIGNATURE_LENGTH)
				throw new IllegalArgumentException();
			w.writeBytes(sig);
		}
		// Hash the message, including the signatures, to get the message ID
		w.removeConsumer(digestingConsumer);
		MessageId id = new MessageId(messageDigest.digest());
		// If the content type is text/plain, extract a subject line
		String subject;
		if(contentType.equals("text/plain")) {
			byte[] start = new byte[Math.min(MAX_SUBJECT_LENGTH, body.length)];
			System.arraycopy(body, 0, start, 0, start.length);
			decoder.reset();
			subject = decoder.decode(ByteBuffer.wrap(start)).toString();
		} else {
			subject = "";
		}
		return new MessageImpl(id, parent, group, author, contentType, subject,
				timestamp, out.toByteArray(), bodyStart, body.length);
	}

	private void writeGroup(Writer w, Group g) throws IOException {
		w.writeStructId(GROUP);
		w.writeString(g.getName());
		byte[] publicKey = g.getPublicKey();
		if(publicKey == null) w.writeNull();
		else w.writeBytes(publicKey);
	}

	private void writeAuthor(Writer w, Author a) throws IOException {
		w.writeStructId(AUTHOR);
		w.writeString(a.getName());
		w.writeBytes(a.getPublicKey());
	}
}
