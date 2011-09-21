package net.sf.briar.api.protocol;

import java.io.IOException;
import java.util.Arrays;

import net.sf.briar.api.serial.Writer;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a group to which
 * users may subscribe.
 */
public class GroupId extends UniqueId {

	public GroupId(byte[] id) {
		super(id);
	}

	public void writeTo(Writer w) throws IOException {
		w.writeUserDefinedId(Types.GROUP_ID);
		w.writeBytes(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof GroupId)
			return Arrays.equals(id, ((GroupId) o).id);
		return false;
	}
}
