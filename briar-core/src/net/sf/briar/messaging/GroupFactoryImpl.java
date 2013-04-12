package net.sf.briar.messaging;

import static net.sf.briar.api.messaging.Types.GROUP;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupFactory;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.LocalGroup;
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
		return createGroup(name, null);
	}

	public Group createGroup(String name, byte[] publicKey) throws IOException {
		GroupId id = getId(name, publicKey);
		return new Group(id, name, publicKey);
	}

	public LocalGroup createLocalGroup(String name, byte[] publicKey,
			byte[] privateKey) throws IOException {
		GroupId id = getId(name, publicKey);
		return new LocalGroup(id, name, publicKey, privateKey);
	}

	private GroupId getId(String name, byte[] publicKey) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(GROUP);
		w.writeString(name);
		if(publicKey == null) w.writeNull();
		else w.writeBytes(publicKey);
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.update(out.toByteArray());
		return new GroupId(messageDigest.digest());
	}
}
