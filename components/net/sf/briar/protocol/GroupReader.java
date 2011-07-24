package net.sf.briar.protocol;

import java.io.IOException;
import java.security.MessageDigest;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class GroupReader implements ObjectReader<Group> {

	private final MessageDigest messageDigest;
	private final GroupFactory groupFactory;

	GroupReader(MessageDigest messageDigest, GroupFactory groupFactory) {
		this.messageDigest = messageDigest;
		this.groupFactory = groupFactory;
	}

	public Group readObject(Reader r) throws IOException {
		// Initialise the consumer
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		// Read and digest the data
		r.addConsumer(digesting);
		r.readUserDefinedTag(Tags.GROUP);
		String name = r.readString();
		boolean restricted = r.readBoolean();
		byte[] saltOrKey = r.readBytes();
		r.removeConsumer(digesting);
		// Build and return the group
		GroupId id = new GroupId(messageDigest.digest());
		return groupFactory.createGroup(id, name, restricted, saltOrKey);
	}
}
