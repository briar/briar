package net.sf.briar.protocol;

import java.io.IOException;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.Writer;

class GroupImpl implements Group {

	private final GroupId id;
	private final String name;
	private final byte[] publicKey;

	GroupImpl(GroupId id, String name, byte[] publicKey) {
		this.id = id;
		this.name = name;
		this.publicKey = publicKey;
	}

	public GroupId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public byte[] getPublicKey() {
		return publicKey;
	}

	public void writeTo(Writer w) throws IOException {
		w.writeUserDefinedTag(Types.GROUP);
		w.writeString(name);
		if(publicKey == null) w.writeNull();
		else w.writeBytes(publicKey);
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
