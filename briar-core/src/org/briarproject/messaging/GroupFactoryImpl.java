package org.briarproject.messaging;

import static org.briarproject.api.messaging.MessagingConstants.GROUP_SALT_LENGTH;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupFactory;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.serial.Writer;
import org.briarproject.api.serial.WriterFactory;

class GroupFactoryImpl implements GroupFactory {

	private final CryptoComponent crypto;
	private final WriterFactory writerFactory;

	@Inject
	GroupFactoryImpl(CryptoComponent crypto, WriterFactory writerFactory) {
		this.crypto = crypto;
		this.writerFactory = writerFactory;
	}

	public Group createGroup(String name) {
		byte[] salt = new byte[GROUP_SALT_LENGTH];
		crypto.getSecureRandom().nextBytes(salt);
		return createGroup(name, salt);
	}

	public Group createGroup(String name, byte[] salt) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		try {
			w.writeListStart();
			w.writeString(name);
			w.writeBytes(salt);
			w.writeListEnd();
		} catch(IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException();
		}
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.update(out.toByteArray());
		GroupId id = new GroupId(messageDigest.digest());
		return new Group(id, name, salt);
	}
}
