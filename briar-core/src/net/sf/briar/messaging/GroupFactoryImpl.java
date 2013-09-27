package net.sf.briar.messaging;

import static net.sf.briar.api.messaging.MessagingConstants.GROUP_SALT_LENGTH;
import static net.sf.briar.api.messaging.Types.GROUP;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupFactory;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class GroupFactoryImpl implements GroupFactory {

	private final CryptoComponent crypto;
	private final WriterFactory writerFactory;

	@Inject
	GroupFactoryImpl(CryptoComponent crypto, WriterFactory writerFactory) {
		this.crypto = crypto;
		this.writerFactory = writerFactory;
	}

	public Group createGroup(String name) throws IOException {
		byte[] salt = new byte[GROUP_SALT_LENGTH];
		crypto.getSecureRandom().nextBytes(salt);
		return createGroup(name, salt);
	}

	public Group createGroup(String name, byte[] salt) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(GROUP);
		w.writeString(name);
		w.writeBytes(salt);
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.update(out.toByteArray());
		GroupId id = new GroupId(messageDigest.digest());
		return new Group(id, name, salt);
	}
}
