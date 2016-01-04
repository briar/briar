package org.briarproject.sync;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;

import static org.briarproject.api.sync.MessagingConstants.GROUP_SALT_LENGTH;

class GroupFactoryImpl implements GroupFactory {

	private final CryptoComponent crypto;
	private final BdfWriterFactory bdfWriterFactory;

	@Inject
	GroupFactoryImpl(CryptoComponent crypto, BdfWriterFactory bdfWriterFactory) {
		this.crypto = crypto;
		this.bdfWriterFactory = bdfWriterFactory;
	}

	public Group createGroup(String name) {
		byte[] salt = new byte[GROUP_SALT_LENGTH];
		crypto.getSecureRandom().nextBytes(salt);
		return createGroup(name, salt);
	}

	public Group createGroup(String name, byte[] salt) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart();
			w.writeString(name);
			w.writeRaw(salt);
			w.writeListEnd();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException();
		}
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.update(out.toByteArray());
		GroupId id = new GroupId(messageDigest.digest());
		return new Group(id, name, salt);
	}
}
