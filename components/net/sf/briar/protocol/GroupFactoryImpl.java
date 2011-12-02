package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Types;
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

	public Group createGroup(String name, byte[] publicKey) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(Types.GROUP);
		w.writeString(name);
		if(publicKey == null) w.writeNull();
		else w.writeBytes(publicKey);
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.reset();
		messageDigest.update(out.toByteArray());
		GroupId id = new GroupId(messageDigest.digest());
		return new GroupImpl(id, name, publicKey);
	}

	public Group createGroup(GroupId id, String name, byte[] publicKey) {
		return new GroupImpl(id, name, publicKey);
	}
}
