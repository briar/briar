package net.sf.briar.messaging;

import static net.sf.briar.api.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_BODY_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MESSAGE_SALT_LENGTH;
import static net.sf.briar.api.messaging.Types.AUTHOR;
import static net.sf.briar.api.messaging.Types.GROUP;
import static net.sf.briar.api.messaging.Types.MESSAGE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.inject.Inject;

import net.sf.briar.api.Author;
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

class MessageFactoryImpl implements MessageFactory {

	private final Signature signature;
	private final SecureRandom random;
	private final MessageDigest messageDigest;
	private final WriterFactory writerFactory;

	@Inject
	MessageFactoryImpl(CryptoComponent crypto, WriterFactory writerFactory) {
		signature = crypto.getSignature();
		random = crypto.getSecureRandom();
		messageDigest = crypto.getMessageDigest();
		this.writerFactory = writerFactory;
	}

	public Message createAnonymousMessage(MessageId parent, Group group,
			String contentType, long timestamp, byte[] body) throws IOException,
			GeneralSecurityException {
		return createMessage(parent, group, null, null, contentType, timestamp,
				body);
	}

	public Message createPseudonymousMessage(MessageId parent, Group group,
			Author author, PrivateKey privateKey, String contentType,
			long timestamp, byte[] body) throws IOException,
			GeneralSecurityException {
		return createMessage(parent, group, author, privateKey, contentType,
				timestamp, body);
	}

	private Message createMessage(MessageId parent, Group group, Author author,
			PrivateKey privateKey, String contentType, long timestamp,
			byte[] body) throws IOException, GeneralSecurityException {
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
		w.writeStructStart(MESSAGE);
		if(parent == null) w.writeNull();
		else w.writeBytes(parent.getBytes());
		writeGroup(w, group);
		if(author == null) w.writeNull();
		else writeAuthor(w, author);
		w.writeString(contentType);
		w.writeIntAny(timestamp);
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
		w.writeStructEnd();
		// Hash the message, including the signature, to get the message ID
		w.removeConsumer(digestingConsumer);
		MessageId id = new MessageId(messageDigest.digest());
		return new MessageImpl(id, parent, group, author, contentType,
				timestamp, out.toByteArray(), bodyStart, body.length);
	}

	private void writeGroup(Writer w, Group g) throws IOException {
		w.writeStructStart(GROUP);
		w.writeString(g.getName());
		w.writeBytes(g.getSalt());
		w.writeStructEnd();
	}

	private void writeAuthor(Writer w, Author a) throws IOException {
		w.writeStructStart(AUTHOR);
		w.writeString(a.getName());
		w.writeBytes(a.getPublicKey());
		w.writeStructEnd();
	}
}
