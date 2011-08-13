package net.sf.briar.protocol;

import java.io.IOException;
import java.security.MessageDigest;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class GroupReader implements ObjectReader<Group> {

	private final MessageDigest messageDigest;
	private final GroupFactory groupFactory;

	GroupReader(CryptoComponent crypto, GroupFactory groupFactory) {
		messageDigest = crypto.getMessageDigest();
		this.groupFactory = groupFactory;
	}

	public Group readObject(Reader r) throws IOException {
		// Initialise the consumer
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		// Read and digest the data
		r.addConsumer(digesting);
		r.readUserDefinedTag(Tags.GROUP);
		String name = r.readString(Group.MAX_NAME_LENGTH);
		byte[] publicKey = null;
		if(r.hasNull()) r.readNull();
		else publicKey = r.readBytes(Group.MAX_PUBLIC_KEY_LENGTH);
		r.removeConsumer(digesting);
		// Build and return the group
		GroupId id = new GroupId(messageDigest.digest());
		return groupFactory.createGroup(id, name, publicKey);
	}
}
