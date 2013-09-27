package net.sf.briar.messaging;

import static net.sf.briar.api.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_BODY_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_SUBJECT_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MESSAGE_SALT_LENGTH;
import static net.sf.briar.api.messaging.Types.AUTHOR;
import static net.sf.briar.api.messaging.Types.GROUP;
import static net.sf.briar.api.messaging.Types.MESSAGE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import net.sf.briar.api.Author;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PrivateKey;
import net.sf.briar.api.crypto.Signature;
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

	private final Signature signature;
	private final SecureRandom random;
	private final MessageDigest messageDigest;
	private final WriterFactory writerFactory;
	private final Clock clock;
	private final CharsetDecoder decoder;

	@Inject
	MessageFactoryImpl(CryptoComponent crypto, WriterFactory writerFactory,
			Clock clock) {
		signature = crypto.getSignature();
		random = crypto.getSecureRandom();
		messageDigest = crypto.getMessageDigest();
		this.writerFactory = writerFactory;
		this.clock = clock;
		decoder = Charset.forName("UTF-8").newDecoder();
	}

	public Message createPrivateMessage(MessageId parent, String contentType,
			byte[] body) throws IOException, GeneralSecurityException {
		return createMessage(parent, null, null, null, contentType, body);
	}

	public Message createAnonymousMessage(MessageId parent, Group group,
			String contentType, byte[] body) throws IOException,
			GeneralSecurityException {
		return createMessage(parent, group, null, null, contentType, body);
	}

	public Message createPseudonymousMessage(MessageId parent, Group group,
			Author author, PrivateKey privateKey, String contentType,
			byte[] body) throws IOException, GeneralSecurityException {
		return createMessage(parent, group, author, privateKey, contentType,
				body);
	}

	private Message createMessage(MessageId parent, Group group, Author author,
			PrivateKey privateKey, String contentType, byte[] body)
					throws IOException, GeneralSecurityException {
		// Validate the arguments
		if((author == null) != (privateKey == null))
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
		Consumer signingConsumer = null;
		if(privateKey != null) {
			signature.initSign(privateKey);
			signingConsumer = new SigningConsumer(signature);
			w.addConsumer(signingConsumer);
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
		byte[] salt = new byte[MESSAGE_SALT_LENGTH];
		random.nextBytes(salt);
		w.writeBytes(salt);
		w.writeBytes(body);
		int bodyStart = (int) counting.getCount() - body.length;
		// Sign the message with the author's private key, if there is one
		if(privateKey == null) {
			w.writeNull();
		} else {
			w.removeConsumer(signingConsumer);
			byte[] sig = signature.sign();
			if(sig.length > MAX_SIGNATURE_LENGTH)
				throw new IllegalArgumentException();
			w.writeBytes(sig);
		}
		// Hash the message, including the signature, to get the message ID
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
		w.writeBytes(g.getSalt());
	}

	private void writeAuthor(Writer w, Author a) throws IOException {
		w.writeStructId(AUTHOR);
		w.writeString(a.getName());
		w.writeBytes(a.getPublicKey());
	}
}
