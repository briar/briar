package net.sf.briar.messaging;

import static net.sf.briar.api.messaging.MessagingConstants.MAX_GROUP_NAME_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PUBLIC_KEY_LENGTH;
import static net.sf.briar.api.messaging.Types.GROUP;

import java.io.IOException;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.serial.DigestingConsumer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class GroupReader implements StructReader<Group> {

	private final MessageDigest messageDigest;

	GroupReader(CryptoComponent crypto) {
		messageDigest = crypto.getMessageDigest();
	}

	public Group readStruct(Reader r) throws IOException {
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		// Read and digest the data
		r.addConsumer(digesting);
		r.readStructId(GROUP);
		String name = r.readString(MAX_GROUP_NAME_LENGTH);
		byte[] publicKey = null;
		if(r.hasNull()) r.readNull();
		else publicKey = r.readBytes(MAX_PUBLIC_KEY_LENGTH);
		r.removeConsumer(digesting);
		// Build and return the group
		GroupId id = new GroupId(messageDigest.digest());
		return new Group(id, name, publicKey);
	}
}
