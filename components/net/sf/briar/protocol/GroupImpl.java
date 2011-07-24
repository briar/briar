package net.sf.briar.protocol;

import java.io.IOException;
import java.security.PublicKey;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Writer;

class GroupImpl implements Group {

	private final GroupId id;
	private final String name;
	private final byte[] salt;
	private final PublicKey publicKey;

	GroupImpl(GroupId id, String name, byte[] salt) {
		this.id = id;
		this.name = name;
		this.salt = salt;
		publicKey = null;
	}

	GroupImpl(GroupId id, String name, PublicKey publicKey) {
		this.id = id;
		this.name = name;
		this.publicKey = publicKey;
		salt = null;
	}

	public GroupId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isRestricted() {
		return salt == null;
	}

	public byte[] getSalt() {
		return salt;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public void writeTo(Writer w) throws IOException {
		w.writeUserDefinedTag(Tags.GROUP);
		w.writeString(name);
		w.writeBoolean(isRestricted());
		if(salt == null) w.writeBytes(publicKey.getEncoded());
		else w.writeBytes(salt);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Group && id.equals(((Group) o).getId());
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
